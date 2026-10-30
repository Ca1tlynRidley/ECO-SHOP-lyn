package dev.lyn.shop.auction;

import dev.lyn.eco.api.EcoApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;
import static dev.lyn.shop.auction.AuctionStore.*;

/** Vanilla chest navigation with two market pages, explicit confirmations, and a durable collection inbox. */
public final class AuctionMenu extends ChestMenu {
    public enum Page {
        BUY_NOW("Buy Now"), BIDDING("Bidding"), MINE("My Listings"), INBOX("Collection"), CREATE("List Held Item");
        public final String title;Page(String title){this.title=title;}
    }
    private static final int PAGE_SIZE=36;
    private final ServerPlayer owner;
    private final AuctionHouse house;
    private final SimpleContainer display;
    @FunctionalInterface private interface Action {void run()throws Exception;}
    private final Map<Integer,Action> actions=new HashMap<>();
    private final String query;
    private Page page;
    private int pageNumber,sort;
    private UUID selected,selectedMail;
    private int selectedRevision;
    private long bidAmount;
    private int heldSlot,quantity,hours;
    private ItemStack held=ItemStack.EMPTY;
    private long price;
    private Kind kind=Kind.BUY_NOW;
    private boolean busy;
    private long nextClick;

    public AuctionMenu(int id,Inventory inventory,ServerPlayer owner,AuctionHouse house,Page page,String query){
        this(id,inventory,owner,house,page,query,new SimpleContainer(54));
    }
    private AuctionMenu(int id,Inventory inventory,ServerPlayer owner,AuctionHouse house,Page page,String query,SimpleContainer display){
        super(MenuType.GENERIC_9x6,id,inventory,display,6);
        this.owner=owner;this.house=house;this.page=page;this.query=query;this.display=display;
        heldSlot=inventory.getSelectedSlot();held=inventory.getItem(heldSlot).copy();quantity=held.getCount();
        price=house.config().defaultPriceCents();hours=house.config().defaultBuyNowHours();draw();
    }
    public void configureListing(Kind kind,long price,int requestedHours){
        if(price<1||price>dev.lyn.eco.api.Money.MAX_BALANCE)throw new IllegalArgumentException("Use a positive price within the economy limit.");
        int duration=requestedHours==0?(kind==Kind.BUY_NOW?house.config().defaultBuyNowHours():house.config().defaultAuctionHours()):requestedHours;
        if(duration<1||duration>house.config().maxDurationHours())throw new IllegalArgumentException("Duration must be between 1 and "+house.config().maxDurationHours()+" hours.");
        this.kind=kind;this.price=price;this.hours=duration;draw();
    }
    public void showListing(UUID id,long requestedBid){
        var listing=house.listing(id);selected=id;selectedMail=null;selectedRevision=listing.revision();
        if(listing.kind()==Kind.BIDDING){long minimum;try{minimum=house.minimumBid(listing);}catch(IllegalArgumentException e){minimum=listing.bid();}
            if(requestedBid!=0&&(requestedBid<minimum||requestedBid>dev.lyn.eco.api.Money.MAX_BALANCE))throw new IllegalArgumentException("Bid is below the current minimum or above the economy limit.");
            bidAmount=requestedBid==0?minimum:requestedBid;
        }
        draw();
    }
    private static Item icon(String id){return dev.lyn.shop.Trades.fresh("minecraft:"+id).getItem();}
    private String money(long cents){return EcoApi.get().format(cents);}
    private static Component text(String value){return Component.literal(value).withStyle(style->style.withItalic(false).withColor(ChatFormatting.GRAY));}
    private void clear(){actions.clear();for(int i=0;i<54;i++)display.setItem(i,label(icon("gray_stained_glass_pane")," "));
        for(int i=0;i<9;i++)display.setItem(i,label(icon("blue_stained_glass_pane")," "));}
    private static ItemStack label(Item icon,String name,String... lore){
        var stack=new ItemStack(icon);stack.set(DataComponents.CUSTOM_NAME,Component.literal(name).withStyle(ChatFormatting.AQUA));
        stack.set(DataComponents.LORE,new ItemLore(Arrays.stream(lore).map(AuctionMenu::text).toList()));return stack;
    }
    private void button(int slot,Item icon,String name,Action action,String... lore){display.setItem(slot,label(icon,name,lore));if(action!=null)actions.put(slot,action);}
    private boolean item(int slot,Lot lot,Action action,String... lines){
        try{var stack=AuctionItems.decode(owner.registryAccess(),lot,house.config().maxItemBytes());
            var lore=new ArrayList<Component>();for(var line:lines)lore.add(text(line));
            var old=stack.get(DataComponents.LORE);if(old!=null){lore.add(Component.empty());old.lines().stream().limit(32).forEach(lore::add);}
            stack.set(DataComponents.LORE,new ItemLore(lore));display.setItem(slot,stack);if(action!=null)actions.put(slot,action);return true;
        }catch(Exception e){button(slot,Items.BARRIER,"Unavailable saved item",action,lot.itemId(),"Restore the item's mod before buying or collecting.","Your saved item has not been removed.");return false;}
    }
    private void footer(){
        button(45,Items.EMERALD,"Buy Now",()->navigate(Page.BUY_NOW),"Browse player-priced bundles");
        button(46,Items.CLOCK,"Bidding",()->navigate(Page.BIDDING),"Separate timed-auction page");
        button(47,Items.ANVIL,"List Held Item",()->navigate(Page.CREATE),"Create a buy-now listing or a bidding auction");
        button(48,Items.BOOK,"My Listings",()->navigate(Page.MINE),"Manage your active listings");
        button(49,Items.CHEST,"Collection ("+house.inbox(owner.getUUID()).size()+")",()->navigate(Page.INBOX),"Won items, returns, earnings and bid refunds");
        button(50,Items.SUNFLOWER,"Refresh",this::refresh);
        button(53,Items.BARRIER,"Close",owner::closeContainer);
    }
    private void navigate(Page next){AuctionModule.open(owner,next,(next==Page.BUY_NOW||next==Page.BIDDING)?query:"");}
    private void refresh(){if(selected!=null){showListing(selected,0);return;}draw();}
    private void draw(){
        clear();button(4,Items.GOLD_INGOT,"Balance: "+money(EcoApi.get().balance(owner.getUUID())),null,"Prices are for the entire displayed bundle.");
        if(selectedMail!=null)drawClaim();else if(selected!=null)drawListing();else switch(page){case CREATE->drawCreate();case INBOX->drawInbox();default->drawBrowser();}
        footer();broadcastChanges();
    }
    private void drawBrowser(){
        var listings=house.book().listings().stream().filter(l->l.expires()>System.currentTimeMillis())
            .filter(l->page==Page.MINE?l.seller().equals(owner.getUUID()):l.kind()==(page==Page.BIDDING?Kind.BIDDING:Kind.BUY_NOW))
            .filter(l->query.isBlank()||(l.lot().itemId()+" "+l.lot().name()+" "+l.sellerName()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
            .sorted(sort==1?Comparator.comparingLong((Listing l)->l.bidder()==null?l.price():l.bid()):sort==2?Comparator.comparingLong(Listing::expires):Comparator.comparingLong(Listing::created).reversed()).toList();
        button(1,page==Page.BIDDING?Items.CLOCK:Items.CHEST,page.title+" | "+listings.size()+" listings",null,"Page "+(Math.clamp(pageNumber,0,Math.max(0,(listings.size()-1)/PAGE_SIZE))+1),"Left-click an item to review it.");
        button(6,Items.NAME_TAG,query.isBlank()?"Search items":"Search: "+query,()->suggest("/ah search "),"Click for the search command");
        button(7,Items.COMPARATOR,"Sort: "+List.of("Newest","Price","Ending soon").get(sort),()->{sort=(sort+1)%3;pageNumber=0;draw();});
        pageNumber=Math.clamp(pageNumber,0,Math.max(0,(listings.size()-1)/PAGE_SIZE));
        for(int i=pageNumber*PAGE_SIZE;i<Math.min(listings.size(),(pageNumber+1)*PAGE_SIZE);i++){
            var l=listings.get(i);String cost=l.kind()==Kind.BUY_NOW?"Buy now: "+money(l.price()):l.bidder()==null?"Starting bid: "+money(l.price()):"Highest bid: "+money(l.bid());
            item(9+i-pageNumber*PAGE_SIZE,l.lot(),()->showListing(l.id(),0),cost,"Seller: "+l.sellerName(),"Remaining: "+remaining(l.expires()),l.lot().count()+" item(s) in this bundle",l.seller().equals(owner.getUUID())?"Your listing — click to manage":"Click to review; confirmation required");
        }
        if(listings.isEmpty())button(22,Items.PAPER,"No listings here",null,"Hold an item and click List Held Item to create one.");
        pagination(listings.size());
    }
    private void pagination(int total){
        if(pageNumber>0)button(51,Items.ARROW,"Previous page",()->{pageNumber--;draw();});
        if((pageNumber+1)*PAGE_SIZE<total)button(52,Items.ARROW,"Next page",()->{pageNumber++;draw();});
    }
    private void drawListing(){
        Listing l;
        try{l=house.listing(selected);}catch(IllegalArgumentException e){selected=null;drawBrowser();return;}
        button(1,Items.PAPER,l.kind()==Kind.BIDDING?"Review Bid":"Review Purchase",null,"Listing: "+l.id(),"Seller: "+l.sellerName());
        boolean decoded=item(13,l.lot(),null,l.lot().count()+" item(s)",l.lot().itemId(),"Seller: "+l.sellerName());
        button(24,Items.CLOCK,"Remaining: "+remaining(l.expires()),null,"Bids near closing can extend the timer.");
        button(40,Items.ARROW,"Back",()->{selected=null;draw();});
        if(l.expires()<=System.currentTimeMillis()){button(31,Items.BARRIER,"Listing expired",null,"Refresh to see settlement and collections.");return;}
        if(l.revision()!=selectedRevision){button(31,Items.BARRIER,"Listing changed",this::refresh,"Click to get a fresh quote before confirming.");return;}
        if(l.seller().equals(owner.getUUID())){
            button(22,Items.GOLD_INGOT,"Listed: "+money(l.bidder()==null?l.price():l.bid()),null,l.bidder()==null?"No bids":"Highest bidder: "+l.bidderName());
            if(l.bidder()==null)button(31,icon("red_stained_glass"),"Cancel my listing",()->{house.cancel(owner.getUUID(),l.id(),false);message("Listing cancelled. Collect your item from the inbox.");navigate(Page.INBOX);},"Returns the item to your collection inbox.");
            else button(31,Items.BARRIER,"Bids are already placed",null,"An auction with bids cannot be cancelled by its seller.");return;
        }
        if(!decoded)return;
        if(l.kind()==Kind.BUY_NOW){
            button(22,Items.GOLD_INGOT,"Total: "+money(l.price()),null,"Whole bundle, not per item");
            button(31,icon("lime_stained_glass"),"Confirm Buy Now",()->{
                AuctionItems.decode(owner.registryAccess(),l.lot(),house.config().maxItemBytes());
                house.buy(owner.getUUID(),owner.getScoreboardName(),l.id(),selectedRevision);message("Purchased for "+money(l.price())+". Click the item in Collection to receive it.");navigate(Page.INBOX);
            },"Pay "+money(l.price()),"Purchased items go to Collection.","Nothing is dropped if your inventory is full.");
        }else{
            long minimum;try{minimum=house.minimumBid(l);}catch(IllegalArgumentException e){button(31,Items.BARRIER,"Maximum bid reached",null);return;}
            bidAmount=Math.max(minimum,bidAmount);button(20,Items.GOLD_INGOT,l.bidder()==null?"Starting: "+money(l.price()):"Highest: "+money(l.bid()),null,l.bidder()==null?"No bids yet":"Bidder: "+l.bidderName());
            button(22,Items.EMERALD,"Your bid: "+money(bidAmount),null,"Minimum: "+money(minimum),"This amount is reserved until outbid or settlement.");
            int[] slots={28,29,30,32,33,34};long[] deltas={-10000,-1000,-100,100,1000,10000};
            for(int i=0;i<slots.length;i++){long delta=deltas[i];button(slots[i],delta>0?icon("lime_stained_glass_pane"):icon("red_stained_glass_pane"),(delta>0?"+":"-")+money(Math.abs(delta)),()->{bidAmount=adjust(bidAmount,delta,minimum);draw();});}
            long quote=bidAmount;button(31,icon("lime_stained_glass"),"Confirm Bid: "+money(quote),()->{
                AuctionItems.decode(owner.registryAccess(),l.lot(),house.config().maxItemBytes());
                house.bid(owner.getUUID(),owner.getScoreboardName(),l.id(),selectedRevision,quote);message("Bid placed: "+money(quote)+". Outbid refunds are collected from /ah inbox.");navigate(Page.BIDDING);
            },owner.getUUID().equals(l.bidder())?"Only the increase is charged.":"Funds are reserved when you confirm.","If outbid, collect your refund from Collection.");
            button(37,Items.NAME_TAG,"Set an exact bid",()->suggest("/ah bid "+l.id()+" "),"Click to enter an exact amount in chat.");
        }
    }
    private void drawCreate(){
        button(1,Items.ANVIL,"List your held item",null,"Choose Buy Now or Bidding, price and duration.","The price is for the entire selected quantity.");
        if(held.isEmpty()){button(22,Items.BARRIER,"Hold an item first",null,"Close this menu, hold an item and reopen List Held Item.");return;}
        var preview=held.copyWithCount(quantity);display.setItem(13,preview);
        int[] slots={19,20,21,23,24,25};long[] deltas={-10000,-1000,-100,100,1000,10000};
        for(int i=0;i<slots.length;i++){long delta=deltas[i];button(slots[i],delta>0?icon("lime_stained_glass_pane"):icon("red_stained_glass_pane"),(delta>0?"+":"-")+money(Math.abs(delta)),()->{price=adjust(price,delta,1);draw();});}
        button(22,Items.GOLD_INGOT,"Bundle price: "+money(price),null,kind==Kind.BUY_NOW?"Buyer pays this total":"Starting bid for this bundle");
        button(28,kind==Kind.BUY_NOW?Items.CHEST:Items.CLOCK,"Mode: "+(kind==Kind.BUY_NOW?"Buy Now":"Bidding"),()->{kind=kind==Kind.BUY_NOW?Kind.BIDDING:Kind.BUY_NOW;hours=kind==Kind.BUY_NOW?house.config().defaultBuyNowHours():house.config().defaultAuctionHours();draw();},"Click to switch listing type");
        button(29,icon("red_stained_glass_pane"),"-1 hour",()->{hours=Math.max(1,hours-1);draw();});
        button(30,Items.CLOCK,hours+" hour(s)",null,"Maximum: "+house.config().maxDurationHours()+" hours");
        button(31,icon("lime_stained_glass_pane"),"+1 hour",()->{hours=Math.min(house.config().maxDurationHours(),hours+1);draw();});
        button(32,icon("red_stained_glass_pane"),"-1 item",()->{quantity=Math.max(1,quantity-1);draw();});
        button(33,Items.PAPER,"Quantity: "+quantity,null,"Available in held stack: "+held.getCount());
        button(34,icon("lime_stained_glass_pane"),"+1 item",()->{quantity=Math.min(held.getCount(),quantity+1);draw();});
        button(40,icon("lime_stained_glass"),"Confirm Listing",()->{
            var plan=AuctionInventory.take(owner,heldSlot,held,quantity);
            var lot=AuctionItems.encode(owner.registryAccess(),held.copyWithCount(quantity),house.config().maxItemBytes());
            house.list(owner.getUUID(),owner.getScoreboardName(),lot,kind,price,hours,()->plan.apply(owner));
            message("Listed "+quantity+" item(s) for "+money(price)+". Track it in My Listings.");navigate(Page.MINE);
        },"Move "+quantity+" item(s) into auction escrow",(kind==Kind.BUY_NOW?"Buy now: ":"Starting bid: ")+money(price),"Duration: "+hours+" hour(s)");
        button(41,Items.NAME_TAG,"Set an exact listing price",()->suggest(kind==Kind.BUY_NOW?"/ah sell ":"/ah auction "),"Command opens this confirmation screen.");
    }
    private void drawInbox(){
        var entries=house.inbox(owner.getUUID()).stream().filter(m->m.lot()!=null).sorted(Comparator.comparingLong(Mail::created).reversed()).toList();
        long cash=house.earnings(owner.getUUID());button(1,Items.CHEST,"Collection | "+entries.size()+" item bundles",null,"Items and money do not expire.","Click an item to review collection.");
        button(4,Items.EMERALD,"Collect money: "+money(cash),cash>0?()->{long paid=house.claimMoney(owner.getUUID(),owner.getScoreboardName());message("Collected "+money(paid)+" from auctions.");draw();}:null,"Sale earnings and outbid refunds",cash==0?"No money waiting":"Click to transfer to your ECO balance");
        pageNumber=Math.clamp(pageNumber,0,Math.max(0,(entries.size()-1)/PAGE_SIZE));
        for(int i=pageNumber*PAGE_SIZE;i<Math.min(entries.size(),(pageNumber+1)*PAGE_SIZE);i++){var m=entries.get(i);item(9+i-pageNumber*PAGE_SIZE,m.lot(),()->{selectedMail=m.id();draw();},m.reason(),m.lot().count()+" item(s)","Click to review collection");}
        if(entries.isEmpty())button(22,Items.PAPER,"No items waiting",null,"Won, returned and purchased items appear here.");pagination(entries.size());
    }
    private void drawClaim(){
        var entry=house.inbox(owner.getUUID()).stream().filter(m->m.id().equals(selectedMail)&&m.lot()!=null).findFirst();
        if(entry.isEmpty()){selectedMail=null;drawInbox();return;}var m=entry.get();
        button(1,Items.CHEST,"Review Collection",null,m.reason());boolean decoded=item(13,m.lot(),null,m.lot().count()+" item(s)",m.lot().itemId());
        if(decoded)button(31,icon("lime_stained_glass"),"Collect Item",()->{
            var stack=AuctionItems.decode(owner.registryAccess(),m.lot(),house.config().maxItemBytes());var plan=AuctionInventory.give(owner,stack);
            house.claimItem(owner.getUUID(),owner.getScoreboardName(),m.id(),()->plan.apply(owner));selectedMail=null;message("Collected "+m.lot().count()+" item(s).");draw();
        },"The entire bundle must fit.","If full, your item stays in Collection.");
        button(40,Items.ARROW,"Back to Collection",()->{selectedMail=null;draw();});
    }
    private static long adjust(long current,long delta,long minimum){return Math.clamp(current+delta,minimum,dev.lyn.eco.api.Money.MAX_BALANCE);}
    private static String remaining(long end){long seconds=Math.max(0,(end-System.currentTimeMillis()+999)/1000);if(seconds>=3600)return seconds/3600+"h "+seconds%3600/60+"m";return seconds/60+"m "+seconds%60+"s";}
    private void message(String value){owner.sendSystemMessage(Component.literal("Auction House: "+value));}
    private void suggest(String command){owner.closeContainer();owner.sendSystemMessage(Component.literal("[Click to enter: "+command+"]").withStyle(style->style.withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(new ClickEvent.SuggestCommand(command))));}
    @Override public boolean stillValid(Player player){return player==owner&&owner.isAlive();}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean canDragTo(Slot slot){return false;}
    @Override public void clicked(int slot,int button,ContainerInput input,Player player){
        if(player!=owner)return;sendAllDataToRemote();if(input!=ContainerInput.PICKUP||button!=0||busy)return;
        if(slot==53){owner.closeContainer();return;}
        long now=System.nanoTime();if(now<nextClick)return;nextClick=now+250_000_000L;
        var action=actions.get(slot);if(action==null)return;busy=true;
        try{house.available();action.run();}catch(Exception e){message(e.getMessage()==null?"Action failed.":e.getMessage());if(house.pending()!=null)owner.closeContainer();else sendAllDataToRemote();}finally{busy=false;}
    }
}
