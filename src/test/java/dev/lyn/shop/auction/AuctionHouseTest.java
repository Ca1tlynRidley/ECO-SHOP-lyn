package dev.lyn.shop.auction;

import dev.lyn.eco.Ledger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static dev.lyn.shop.auction.AuctionStore.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionHouseTest {
    @TempDir Path temporary;
    private Ledger economy;
    private AuctionStore store;
    private AuctionHouse house;
    private final AtomicLong now=new AtomicLong(1_800_000_000_000L);
    private final UUID seller=offline("Seller"),buyer=offline("Buyer"),other=offline("OtherBuyer");
    private static UUID offline(String name){return UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));}
    private static Lot lot(){return new Lot("saved-components","urlmusicdisc:custom_disc","Custom music disc",1);}
    @BeforeEach void setup()throws Exception{
        economy=new Ledger(temporary.resolve("economy/accounts.json"),0,"$");
        economy.ensureAccount(seller,"Seller");economy.ensureAccount(buyer,"Buyer");economy.ensureAccount(other,"OtherBuyer");
        assertTrue(economy.deposit(buyer,100_000,"test-funds").success());assertTrue(economy.deposit(other,100_000,"test-funds").success());
        store=new AuctionStore(temporary.resolve("market/auctions.json"));house=new AuctionHouse(store,economy,AuctionConfig.defaults(),now::get);
    }
    private Listing listing(Kind kind,long price)throws Exception{return house.list(seller,"Seller",lot(),kind,price,1,()->{});}
    @Test void buyNowEscrowsMoneyAndDeliversOriginalLotThroughCollection()throws Exception{
        var l=listing(Kind.BUY_NOW,1250);house.buy(buyer,"Buyer",l.id(),l.revision());
        assertEquals(98_750,economy.balance(buyer));assertEquals(0,economy.balance(seller));assertEquals(1250,house.earnings(seller));
        assertEquals(lot(),house.inbox(buyer).getFirst().lot());assertTrue(house.book().listings().isEmpty());
        assertEquals(1250,house.claimMoney(seller,"Seller"));assertEquals(1250,economy.balance(seller));assertEquals(0,economy.balance(house.escrow()));
        var entry=house.inbox(buyer).getFirst();var deliveries=new AtomicInteger();house.claimItem(buyer,"Buyer",entry.id(),deliveries::incrementAndGet);
        assertEquals(1,deliveries.get());assertThrows(IllegalArgumentException.class,()->house.claimItem(buyer,"Buyer",entry.id(),deliveries::incrementAndGet));
    }
    @Test void duplicatePurchaseAndSelfPurchaseCannotChargeMoney()throws Exception{
        var l=listing(Kind.BUY_NOW,1000);assertThrows(IllegalArgumentException.class,()->house.buy(seller,"Seller",l.id(),0));
        house.buy(buyer,"Buyer",l.id(),0);assertThrows(IllegalArgumentException.class,()->house.buy(other,"OtherBuyer",l.id(),0));assertEquals(100_000,economy.balance(other));
    }
    @Test void insufficientFundsLeaveListingAndInventoryEntitlementsUnchanged()throws Exception{
        var l=listing(Kind.BUY_NOW,200_000);assertThrows(IllegalArgumentException.class,()->house.buy(buyer,"Buyer",l.id(),0));
        assertEquals(l,house.listing(l.id()));assertNull(house.pending());assertTrue(house.inbox(buyer).isEmpty());assertEquals(100_000,economy.balance(buyer));
    }
    @Test void outbidFundsWaitSafelyInInboxAndSellerCanBeOffline()throws Exception{
        var l=listing(Kind.BIDDING,1000);house.bid(buyer,"Buyer",l.id(),0,1000);house.bid(other,"OtherBuyer",l.id(),1,1500);
        assertEquals(99_000,economy.balance(buyer));assertEquals(98_500,economy.balance(other));assertEquals(1000,house.earnings(buyer));
        assertEquals(2500,economy.balance(house.escrow()));house.claimMoney(buyer,"Buyer");assertEquals(100_000,economy.balance(buyer));
        now.addAndGet(3_600_001);house.expire();assertEquals(1500,house.earnings(seller));assertEquals(lot(),house.inbox(other).getFirst().lot());
        house.expire();assertEquals(1,house.inbox(other).size());house.claimMoney(seller,"Seller");assertEquals(1500,economy.balance(seller));
    }
    @Test void raisingOwnBidOnlyReservesTheDifference()throws Exception{
        var l=listing(Kind.BIDDING,1000);house.bid(buyer,"Buyer",l.id(),0,1000);house.bid(buyer,"Buyer",l.id(),1,1500);
        assertEquals(98_500,economy.balance(buyer));assertEquals(1500,economy.balance(house.escrow()));assertEquals(0,house.earnings(buyer));
    }
    @Test void staleOrUnderMinimumBidsAndSelfBidsAreRejected()throws Exception{
        var l=listing(Kind.BIDDING,1000);assertThrows(IllegalArgumentException.class,()->house.bid(seller,"Seller",l.id(),0,1000));
        house.bid(buyer,"Buyer",l.id(),0,1000);
        assertThrows(IllegalArgumentException.class,()->house.bid(other,"OtherBuyer",l.id(),0,1500));
        assertThrows(IllegalArgumentException.class,()->house.bid(other,"OtherBuyer",l.id(),1,1001));assertEquals(100_000,economy.balance(other));
    }
    @Test void failedOutbidDoesNotRefundOrReplaceTheExistingBid()throws Exception{
        var l=listing(Kind.BIDDING,1000);house.bid(buyer,"Buyer",l.id(),0,1000);
        assertThrows(IllegalArgumentException.class,()->house.bid(other,"OtherBuyer",l.id(),1,200_000));
        assertEquals(buyer,house.listing(l.id()).bidder());assertEquals(0,house.earnings(buyer));assertEquals(1000,economy.balance(house.escrow()));
    }
    @Test void antiSnipingExtendsOnlyNearClosingAndUsesPercentageIncrement()throws Exception{
        var l=listing(Kind.BIDDING,20_000);now.set(l.expires()-10_000);house.bid(buyer,"Buyer",l.id(),0,20_000);
        var updated=house.listing(l.id());assertEquals(now.get()+120_000,updated.expires());assertEquals(21_000,house.minimumBid(updated));
    }
    @Test void cancellationReturnsItemsAndProtectsExistingBidders()throws Exception{
        var l=listing(Kind.BIDDING,1000);house.bid(buyer,"Buyer",l.id(),0,1000);
        assertThrows(IllegalArgumentException.class,()->house.cancel(seller,l.id(),false));assertThrows(IllegalArgumentException.class,()->house.cancel(other,l.id(),false));
        house.cancel(seller,l.id(),true);assertEquals(lot(),house.inbox(seller).getFirst().lot());assertEquals(1000,house.earnings(buyer));
    }
    @Test void ownerCanCancelAnUnbidListing()throws Exception{
        var l=listing(Kind.BUY_NOW,1000);house.cancel(seller,l.id(),false);assertTrue(house.book().listings().isEmpty());assertEquals(lot(),house.inbox(seller).getFirst().lot());
    }
    @Test void restartExpiresUnbidItemsAndSettlesWinningBidExactlyOnce()throws Exception{
        var buy=listing(Kind.BUY_NOW,1000);var bid=listing(Kind.BIDDING,2000);house.bid(buyer,"Buyer",bid.id(),0,2000);
        now.set(buy.expires()+1);var restarted=new AuctionHouse(new AuctionStore(temporary.resolve("market/auctions.json")),new Ledger(temporary.resolve("economy/accounts.json"),0,"$"),AuctionConfig.defaults(),now::get);
        restarted.expire();restarted.expire();assertEquals(1,restarted.inbox(buyer).size());assertEquals(2,restarted.inbox(seller).size());assertEquals(2000,restarted.earnings(seller));
    }
    @Test void offlineUuidSurvivesReconnectWithoutMojangLookupAndNamesDoNotGrantOwnership()throws Exception{
        var l=listing(Kind.BUY_NOW,1000);house.buy(buyer,"Buyer",l.id(),0);var mail=house.inbox(buyer).getFirst();
        assertEquals(buyer,offline("Buyer"));assertThrows(IllegalArgumentException.class,()->house.claimItem(offline("buyer"),"buyer",mail.id(),()->fail("Wrong identity received an item")));
        house.claimItem(offline("Buyer"),"Buyer",mail.id(),()->{});assertTrue(house.inbox(buyer).isEmpty());
    }
    @Test void onlineUuidAndRenamedDisplayNameKeepTheirOwnCollection()throws Exception{
        UUID online=UUID.randomUUID();house.ensure(online,"OldName");economy.deposit(online,2000,"funds");var l=listing(Kind.BUY_NOW,1000);
        house.buy(online,"NewName",l.id(),0);assertEquals(online,house.inbox(online).getFirst().owner());
        assertTrue(house.inbox(offline("NewName")).isEmpty());
    }
    @Test void defaultsAllowRareAndModdedItemsButConfigBlocksExactIdsAndNamespaces()throws Exception{
        assertTrue(house.config().allows("urlmusicdisc:custom_disc"));assertTrue(house.config().allows("minecraft:netherite_ingot"));assertTrue(house.config().allows("minecraft:dragon_egg"));
        var c=AuctionConfig.defaults();house.configure(new AuctionConfig(c.enabled(),c.maxListingsPerPlayer(),c.maxActiveListings(),c.defaultBuyNowHours(),c.defaultAuctionHours(),c.maxDurationHours(),c.defaultPriceCents(),c.minimumBidIncrementCents(),c.bidIncrementPercent(),c.antiSnipeSeconds(),c.maxItemBytes(),List.of("urlmusicdisc:*","minecraft:dragon_egg")));
        assertFalse(house.config().allows("minecraft:dragon_egg"));assertThrows(IllegalArgumentException.class,()->listing(Kind.BUY_NOW,100));
        assertTrue(house.book().listings().isEmpty());
    }
    @Test void listingLimitAndInvalidDurationNeverRemoveHeldItems()throws Exception{
        var c=AuctionConfig.defaults();house.configure(new AuctionConfig(true,1,2,1,1,2,100,1,5,0,1024,List.of()));listing(Kind.BUY_NOW,100);
        assertThrows(IllegalArgumentException.class,()->house.list(seller,"Seller",lot(),Kind.BUY_NOW,100,1,()->fail("Item removed despite limit")));
        assertThrows(IllegalArgumentException.class,()->house.list(other,"OtherBuyer",lot(),Kind.BUY_NOW,100,3,()->fail("Item removed despite bad duration")));
    }
    @Test void failedCollectionPaymentRetainsMoneyEntitlement()throws Exception{
        var l=listing(Kind.BUY_NOW,1000);house.buy(buyer,"Buyer",l.id(),0);economy.setBalance(seller,dev.lyn.eco.api.Money.MAX_BALANCE,"test-cap");
        assertThrows(IllegalArgumentException.class,()->house.claimMoney(seller,"Seller"));assertEquals(1000,house.earnings(seller));assertNull(house.pending());
    }
    @Test void uncertainInventoryOperationPausesInsteadOfRepeatingOrDroppingTheItem()throws Exception{
        var l=listing(Kind.BUY_NOW,1000);house.buy(buyer,"Buyer",l.id(),0);var mail=house.inbox(buyer).getFirst();var deliveries=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->house.claimItem(buyer,"Buyer",mail.id(),()->{deliveries.incrementAndGet();throw new IOException("Player save failed");}));
        assertNotNull(house.pending());assertEquals(1,deliveries.get());assertThrows(IllegalStateException.class,()->house.claimItem(buyer,"Buyer",mail.id(),deliveries::incrementAndGet));
        var restarted=new AuctionHouse(new AuctionStore(temporary.resolve("market/auctions.json")),economy,AuctionConfig.defaults(),now::get);assertNotNull(restarted.pending());assertEquals(1,deliveries.get());
    }
    @Test void finalWriteFailureAfterPaymentCanBeResolvedWithoutChargingTwice()throws Exception{
        var failFinal=new AtomicBoolean();Path file=temporary.resolve("failure/auctions.json");
        var failing=new AuctionStore(file,(target,json)->{if(failFinal.get()&&!json.contains("\"pending\": {"))throw new IOException("Disk failure after transfer");AuctionStore.atomicWrite(target,json);});
        var market=new AuctionHouse(failing,economy,AuctionConfig.defaults(),now::get);var l=market.list(seller,"Seller",lot(),Kind.BUY_NOW,1000,1,()->{});
        failFinal.set(true);assertThrows(IllegalStateException.class,()->market.buy(buyer,"Buyer",l.id(),0));assertEquals(99_000,economy.balance(buyer));
        assertThrows(IllegalStateException.class,()->market.buy(buyer,"Buyer",l.id(),0));assertEquals(99_000,economy.balance(buyer));
        var restarted=new AuctionHouse(new AuctionStore(file),economy,AuctionConfig.defaults(),now::get);assertNotNull(restarted.pending());
        restarted.resolve(restarted.pending().id(),true);assertEquals(1000,restarted.earnings(seller));assertEquals(1,restarted.inbox(buyer).size());assertTrue(restarted.book().listings().isEmpty());
    }
    @Test void prepareWriteFailureNeverTouchesTheInventoryOrFunds()throws Exception{
        Path file=temporary.resolve("prepare/auctions.json");var block=new AtomicBoolean();
        var failing=new AuctionStore(file,(target,json)->{if(block.get())throw new IOException("Disk full");AuctionStore.atomicWrite(target,json);});
        var market=new AuctionHouse(failing,economy,AuctionConfig.defaults(),now::get);block.set(true);
        assertThrows(IOException.class,()->market.list(seller,"Seller",lot(),Kind.BUY_NOW,100,1,()->fail("Inventory touched before journal saved")));assertNull(market.pending());
    }
    @Test void missingEconomyAccountOrDamagedAuctionDataIsNotSilentlyReset()throws Exception{
        var l=listing(Kind.BIDDING,1000);house.bid(buyer,"Buyer",l.id(),0,1000);
        var empty=new Ledger(temporary.resolve("empty/accounts.json"),0,"$");assertThrows(IllegalStateException.class,()->new AuctionHouse(store,empty,AuctionConfig.defaults(),now::get));
        Path file=temporary.resolve("market/auctions.json");Files.writeString(file,"broken");assertThrows(IOException.class,()->new AuctionStore(file));
    }
    @Test void operatorCanReassignOfflineOwnershipAfterAnIntentionalUuidChange()throws Exception{
        var l=listing(Kind.BUY_NOW,1000);house.buy(buyer,"Buyer",l.id(),0);UUID replacement=offline("buyer");
        assertEquals(1,house.reassign(buyer,replacement,"buyer"));assertTrue(house.inbox(buyer).isEmpty());assertEquals(1,house.inbox(replacement).size());
        var mail=house.inbox(replacement).getFirst();house.claimItem(replacement,"buyer",mail.id(),()->{});
        assertEquals(99_000,economy.balance(buyer));assertEquals(0,economy.balance(replacement));
    }
    @Test void reassignmentCannotTurnAnActiveBidIntoASelfBid()throws Exception{
        var l=listing(Kind.BIDDING,1000);house.bid(buyer,"Buyer",l.id(),0,1000);
        assertThrows(IllegalArgumentException.class,()->house.reassign(seller,buyer,"Buyer"));assertEquals(seller,house.listing(l.id()).seller());
    }
    @Test void internalEscrowDoesNotReceiveAPlayerStartingBalance()throws Exception{
        var fundedDefault=new Ledger(temporary.resolve("starting/accounts.json"),5000,"$");var newStore=new AuctionStore(temporary.resolve("starting/auctions.json"));
        var market=new AuctionHouse(newStore,fundedDefault,AuctionConfig.defaults(),now::get);assertEquals(0,fundedDefault.balance(market.escrow()));
        market.ensure(buyer,"Buyer");assertEquals(5000,fundedDefault.balance(buyer));
    }
    @Test void externalEscrowWithdrawalPausesPaymentsAndLeavesAllEntitlementsIntact()throws Exception{
        var l=listing(Kind.BUY_NOW,1000);house.buy(buyer,"Buyer",l.id(),0);economy.setBalance(house.escrow(),0,"simulate-admin-mistake");
        assertThrows(IllegalStateException.class,()->house.claimMoney(seller,"Seller"));assertEquals(1000,house.earnings(seller));assertEquals(1,house.inbox(buyer).size());
        economy.deposit(house.escrow(),1000,"reconcile-test");assertEquals(1000,house.claimMoney(seller,"Seller"));
    }
}
