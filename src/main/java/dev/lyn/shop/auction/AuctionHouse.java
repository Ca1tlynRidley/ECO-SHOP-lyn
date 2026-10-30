package dev.lyn.shop.auction;

import dev.lyn.eco.api.*;
import java.io.IOException;
import java.util.*;
import java.util.function.LongSupplier;
import static dev.lyn.shop.auction.AuctionStore.*;

/** All public operations run on the server thread. UUIDs always come from the connected server player. */
public final class AuctionHouse {
    @FunctionalInterface public interface InventoryEffect {void apply()throws Exception;}
    @FunctionalInterface private interface Effect {EconomyService.Result apply()throws Exception;}
    private final AuctionStore store;
    private final EconomyService economy;
    private final LongSupplier clock;
    private AuctionConfig config;

    public AuctionHouse(AuctionStore store,EconomyService economy,AuctionConfig config,LongSupplier clock){
        this.store=store;this.economy=economy;this.config=config;this.clock=clock;
        UUID escrow=store.state().escrow();
        boolean newEscrow=economy.findAccount(escrow.toString()).isEmpty();
        if(newEscrow&&reserved(store.state().book())>0)
            throw new IllegalStateException("Auction escrow account is missing. Restore the matching economy backup.");
        economy.ensureAccount(escrow,"LynAuctionHouse");
        if(newEscrow&&!economy.setBalance(escrow,0,"ah:initialize-escrow").success())
            throw new IllegalStateException("Could not initialize the auction escrow account.");
        if(store.state().pending()==null)checkReserve(store.state().book());
    }
    public AuctionConfig config(){return config;}
    public void configure(AuctionConfig next){config=next;}
    public Book book(){return store.state().book();}
    public Pending pending(){return store.state().pending();}
    public UUID escrow(){return store.state().escrow();}
    public void available(){store.requireClear();checkReserve(book());}
    public void ensure(UUID id,String name){if(id.equals(escrow()))throw new IllegalArgumentException("Invalid player account");economy.ensureAccount(id,name);}
    public List<Mail> inbox(UUID owner){return book().mail().stream().filter(m->m.owner().equals(owner)).toList();}
    public long earnings(UUID owner){long total=0;for(var m:inbox(owner))total=Math.addExact(total,m.cents());return total;}
    public Listing listing(UUID id){return book().listings().stream().filter(l->l.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("This listing is no longer available. Refresh the page."));}

    public Listing list(UUID seller,String name,Lot lot,Kind kind,long price,int hours,InventoryEffect removeHeld)throws IOException{
        available();if(!config.enabled())throw new IllegalArgumentException("New auction listings are disabled.");
        if(!config.allows(lot.itemId()))throw new IllegalArgumentException("This item is blacklisted from player auctions.");
        if(price<1||price>Money.MAX_BALANCE||hours<1||hours>config.maxDurationHours())throw new IllegalArgumentException("Invalid price or duration.");
        if(book().listings().size()>=config.maxActiveListings()||book().listings().stream().filter(l->l.seller().equals(seller)).count()>=config.maxListingsPerPlayer())
            throw new IllegalArgumentException("Active listing limit reached. Collect or cancel an old listing.");
        ensure(seller,name);long now=clock.getAsLong();
        var listing=new Listing(UUID.randomUUID(),seller,name,lot,kind,price,now,Math.addExact(now,hours*3_600_000L),null,null,0,0);
        var listings=new ArrayList<>(book().listings());listings.add(listing);
        external("LIST",seller,name,"listing="+listing.id()+" item="+lot.itemId()+" count="+lot.count(),new Book(listings,book().mail()),()->{removeHeld.apply();return ok();});
        return listing;
    }
    public void buy(UUID buyer,String name,UUID id,int revision)throws IOException{
        available();var listing=live(id,revision);if(listing.kind()!=Kind.BUY_NOW)throw new IllegalArgumentException("Use the bidding page for this listing.");
        if(listing.seller().equals(buyer))throw new IllegalArgumentException("You cannot buy your own listing.");
        ensure(buyer,name);var mail=new ArrayList<>(book().mail());
        mail.add(itemMail(buyer,listing.lot(),"Purchased from "+listing.sellerName()));
        mail.add(moneyMail(listing.seller(),listing.price(),"Sale: "+listing.lot().name()));
        external("BUY",buyer,name,"listing="+id+" cents="+listing.price(),without(listing,mail),
            ()->economy.transfer(buyer,escrow(),listing.price(),"ah:buy:"+id));
    }
    public long minimumBid(Listing listing){
        if(listing.bidder()==null)return listing.price();
        long step=Math.max(config.minimumBidIncrementCents(),(Math.multiplyExact(listing.bid(),config.bidIncrementPercent())+99)/100);
        long next=Math.addExact(listing.bid(),step);if(next>Money.MAX_BALANCE)throw new IllegalArgumentException("Maximum bid reached.");return next;
    }
    public void bid(UUID bidder,String name,UUID id,int revision,long amount)throws IOException{
        available();var listing=live(id,revision);if(listing.kind()!=Kind.BIDDING)throw new IllegalArgumentException("This is a buy-now listing.");
        if(listing.seller().equals(bidder))throw new IllegalArgumentException("You cannot bid on your own item.");
        if(amount<minimumBid(listing)||amount>Money.MAX_BALANCE)throw new IllegalArgumentException("Bid is too low or above the economy limit. Refresh the listing.");
        ensure(bidder,name);boolean ownBid=bidder.equals(listing.bidder());long charge=amount-(ownBid?listing.bid():0);
        var mail=new ArrayList<>(book().mail());
        if(listing.bidder()!=null&&!ownBid)mail.add(moneyMail(listing.bidder(),listing.bid(),"Outbid refund: "+listing.lot().name()));
        long end=listing.expires(),window=config.antiSnipeSeconds()*1000L,now=clock.getAsLong();if(window>0&&end-now<window)end=Math.addExact(now,window);
        var next=new Listing(listing.id(),listing.seller(),listing.sellerName(),listing.lot(),listing.kind(),listing.price(),listing.created(),end,bidder,name,amount,Math.incrementExact(listing.revision()));
        var listings=new ArrayList<>(book().listings());listings.set(listings.indexOf(listing),next);
        external("BID",bidder,name,"listing="+id+" charge="+charge+" totalBid="+amount,new Book(listings,mail),
            ()->economy.transfer(bidder,escrow(),charge,"ah:bid:"+id));
    }
    public void cancel(UUID owner,UUID id,boolean administrator)throws IOException{
        available();var listing=listing(id);
        if(!administrator&&!listing.seller().equals(owner))throw new IllegalArgumentException("Only the owner can cancel this listing.");
        if(!administrator&&listing.bidder()!=null)throw new IllegalArgumentException("An auction with bids cannot be cancelled.");
        var mail=new ArrayList<>(book().mail());mail.add(itemMail(listing.seller(),listing.lot(),"Cancelled listing"));
        if(listing.bidder()!=null)mail.add(moneyMail(listing.bidder(),listing.bid(),"Administrator cancelled auction"));
        store.update(without(listing,mail));
    }
    public void expire()throws IOException{
        if(pending()!=null)return;
        long now=clock.getAsLong();var expired=book().listings().stream().filter(l->l.expires()<=now).toList();if(expired.isEmpty())return;
        var listings=new ArrayList<>(book().listings());var mail=new ArrayList<>(book().mail());
        for(var l:expired){
            listings.remove(l);
            if(l.bidder()==null)mail.add(itemMail(l.seller(),l.lot(),"Expired listing"));
            else{mail.add(itemMail(l.bidder(),l.lot(),"Won auction from "+l.sellerName()));mail.add(moneyMail(l.seller(),l.bid(),"Auction earnings: "+l.lot().name()));}
        }
        store.update(new Book(listings,mail));
    }
    public void claimItem(UUID owner,String name,UUID mailId,InventoryEffect deliver)throws IOException{
        available();Mail entry=inbox(owner).stream().filter(m->m.id().equals(mailId)&&m.lot()!=null).findFirst().orElseThrow(()->new IllegalArgumentException("This item was already collected or is not yours."));
        var mail=new ArrayList<>(book().mail());mail.remove(entry);
        external("CLAIM_ITEM",owner,name,"mail="+mailId+" item="+entry.lot().itemId()+" count="+entry.lot().count(),new Book(book().listings(),mail),()->{deliver.apply();return ok();});
    }
    public long claimMoney(UUID owner,String name)throws IOException{
        available();ensure(owner,name);long amount=earnings(owner);if(amount==0)throw new IllegalArgumentException("No money is waiting to be collected.");
        if(amount>Money.MAX_BALANCE)throw new IllegalArgumentException("Contact an administrator: collection total exceeds the economy limit.");
        var mail=book().mail().stream().filter(m->!m.owner().equals(owner)||m.cents()==0).toList();
        external("CLAIM_MONEY",owner,name,"cents="+amount,new Book(book().listings(),mail),()->economy.transfer(escrow(),owner,amount,"ah:collect:"+owner));return amount;
    }
    /** Explicit operator-only ownership repair; no name-based automatic account merging. */
    public int reassign(UUID oldId,UUID newId,String newName)throws IOException{
        available();if(oldId.equals(newId)||oldId.equals(escrow())||newId.equals(escrow()))throw new IllegalArgumentException("Choose distinct player UUIDs, not the escrow account.");
        int moved=0;var listings=new ArrayList<Listing>();var mail=new ArrayList<Mail>();
        for(var l:book().listings()){
            boolean seller=l.seller().equals(oldId),bidder=oldId.equals(l.bidder());if(seller||bidder)moved++;
            UUID sellerId=seller?newId:l.seller(),bidderId=bidder?newId:l.bidder();
            if(sellerId.equals(bidderId))throw new IllegalArgumentException("Reassignment would create a self-bid. Cancel or settle listing "+l.id()+" first.");
            listings.add(new Listing(l.id(),sellerId,seller?newName:l.sellerName(),l.lot(),l.kind(),l.price(),l.created(),l.expires(),bidderId,bidder?newName:l.bidderName(),l.bid(),seller||bidder?Math.incrementExact(l.revision()):l.revision()));
        }
        for(var m:book().mail()){boolean change=m.owner().equals(oldId);if(change)moved++;mail.add(change?new Mail(m.id(),newId,m.lot(),m.cents(),m.reason(),m.created()):m);}
        if(moved==0)throw new IllegalArgumentException("No auction listings, bids or collection entries belong to that UUID.");
        ensure(newId,newName);store.update(new Book(listings,mail));
        System.getLogger("SHOP-MOD-lyn").log(System.Logger.Level.WARNING,"Administrator reassigned "+moved+" auction records from "+oldId+" to "+newId);
        return moved;
    }
    public void resolve(UUID reference,boolean commit)throws IOException{
        var p=pending();if(p==null||!p.id().equals(reference))throw new IllegalArgumentException("Pending operation reference does not match.");
        checkReserve(commit?p.after():book());
        System.getLogger("SHOP-MOD-lyn").log(System.Logger.Level.WARNING,"Administrator resolved auction operation "+p.id()+" kind="+p.kind()+" actor="+p.actor()+" details="+p.details()+" commit="+commit);
        if(commit)store.complete();else store.abort();
    }
    private Listing live(UUID id,int revision){var l=listing(id);if(l.expires()<=clock.getAsLong())throw new IllegalArgumentException("This listing has expired. Refresh the page.");if(l.revision()!=revision)throw new IllegalArgumentException("This listing changed. Refresh before confirming.");return l;}
    private Book without(Listing listing,List<Mail> mail){return new Book(book().listings().stream().filter(l->!l.id().equals(listing.id())).toList(),mail);}
    private Mail itemMail(UUID owner,Lot lot,String reason){return new Mail(UUID.randomUUID(),owner,lot,0,reason,clock.getAsLong());}
    private Mail moneyMail(UUID owner,long cents,String reason){return new Mail(UUID.randomUUID(),owner,null,cents,reason,clock.getAsLong());}
    private void checkReserve(Book book){if(economy.balance(escrow())<reserved(book))throw new IllegalStateException("Auction escrow is underfunded. Restore matching auction/economy backups or reconcile with an administrator.");}
    private static EconomyService.Result ok(){return new EconomyService.Result(EconomyService.Status.SUCCESS,null);}
    private void external(String kind,UUID actor,String name,String details,Book after,Effect effect)throws IOException{
        var pending=store.prepare(kind,actor,name,details,clock.getAsLong(),after);
        EconomyService.Result result;
        try{result=effect.apply();}catch(Exception e){throw paused(pending,e);}
        if(!result.success()){
            try{store.abort();}catch(IOException e){throw paused(pending,e);}
            throw new IllegalArgumentException("Payment failed: "+result.status()+". No item or bid was changed.");
        }
        if(result.transactionId()!=null)System.getLogger("SHOP-MOD-lyn").log(System.Logger.Level.INFO,"Auction operation "+pending.id()+" economy transaction="+result.transactionId()+" kind="+kind+" "+details);
        try{store.complete();}catch(IOException e){throw paused(pending,e);}
    }
    private static IllegalStateException paused(Pending pending,Exception error){
        System.getLogger("SHOP-MOD-lyn").log(System.Logger.Level.ERROR,"Auction operation needs review: "+pending.id()+" "+pending.kind()+" "+pending.details(),error);
        return new IllegalStateException("Auction operation paused for administrator review. Do not retry. Reference: "+pending.id(),error);
    }
}
