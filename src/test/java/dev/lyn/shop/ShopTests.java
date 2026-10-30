package dev.lyn.shop;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShopTests {
    @TempDir Path temporary;
    private static ShopCatalog defaults()throws Exception{try(var stream=ShopTests.class.getResourceAsStream("/default-prices.json")){return ShopCatalog.parse(new String(Objects.requireNonNull(stream).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}
    @Test void everyPurchasableItemHasAStrictlyLowerPositiveSellPrice()throws Exception{
        ShopCatalog catalog=defaults();assertTrue(catalog.items().size()>1000);
        for(var product:catalog.items().values()){assertTrue(product.sell()>0,product.id());assertTrue(product.sell()<product.buy(),product.id());}
    }
    @Test void regularEggsAndNewColoredEggsCanAlwaysBeSold()throws Exception{
        for(String id:List.of("egg","blue_egg","brown_egg")){var p=defaults().items().get("minecraft:"+id);assertNotNull(p,id);assertTrue(p.sell()>0);}
    }
    @Test void rareRewardsAdminItemsAndModdedMusicDiscsAreExcluded()throws Exception{
        var catalog=defaults();
        for(String id:List.of("minecraft:netherite_ingot","minecraft:netherite_sword","minecraft:netherite_upgrade_smithing_template","minecraft:ancient_debris","minecraft:dragon_egg","minecraft:dragon_head","minecraft:end_portal_frame","minecraft:elytra","minecraft:mace","minecraft:heavy_core","minecraft:nether_star","minecraft:enchanted_golden_apple","minecraft:diamond_spawn_egg","urlmusicdisc:disc")){
            assertFalse(CatalogPolicy.allowed(id),id);assertFalse(catalog.items().containsKey(id),id);
        }
    }
    @Test void diamondsAndEmeraldsHaveExpensiveDefaultPrices()throws Exception{
        assertEquals(500000,defaults().items().get("minecraft:diamond").buy());
        assertEquals(750000,defaults().items().get("minecraft:emerald").buy());
        assertEquals(2500,defaults().items().get("minecraft:diamond").sell());
    }
    @Test void includesCurrentVersionItems()throws Exception{
        var c=defaults();for(String id:List.of("minecraft:sulfur","minecraft:cinnabar","minecraft:pale_oak_log","minecraft:resin_clump","minecraft:copper_sword"))assertTrue(c.items().containsKey(id),id);
        assertTrue(c.category("new").size()>100);
    }
    @Test void rejectsPriceInversionFractionalCentsAndExcludedConfigEntries(){
        String prefix="{\"schemaVersion\":1,\"maxPurchase\":2304,\"sellCooldownSeconds\":{\"iron\":120},\"items\":{";
        for(String entry:List.of("\"minecraft:egg\":{\"category\":\"food\",\"buy\":\"10\",\"sell\":\"10\"}","\"minecraft:egg\":{\"category\":\"food\",\"buy\":\"0.001\",\"sell\":\"0.01\"}","\"minecraft:dragon_egg\":{\"category\":\"food\",\"buy\":\"10\",\"sell\":\"1\"}"))assertThrows(IllegalArgumentException.class,()->ShopCatalog.parse(prefix+entry+"}}"));
    }
    @Test void purchasePlanMergesExistingStacksThenUsesEmptySlots(){
        int[] counts={60,0,64};boolean[] same={true,false,false};
        int[] result=TradeMath.placement(counts,same,64,10);
        assertArrayEquals(new int[]{64,6,64},result);assertArrayEquals(new int[]{60,0,64},counts);
    }
    @Test void fullInventoryReturnsNoPlanAndNeverOverwritesUnrelatedItems(){
        assertNull(TradeMath.placement(new int[]{64,10},new boolean[]{true,false},64,1));
        assertArrayEquals(new int[]{64,10},TradeMath.placement(new int[]{63,10},new boolean[]{true,false},64,1));
    }
    @Test void unstackableGearRequiresOneEmptySlotPerItem(){
        assertArrayEquals(new int[]{1,1,1},TradeMath.placement(new int[]{0,0,0},new boolean[3],1,3));
        assertNull(TradeMath.placement(new int[]{0,0,0},new boolean[3],1,4));
    }
    @Test void totalsUseExactCentsAndRejectOutOfRangeQuantities(){
        assertEquals(1500000,TradeMath.total(500000,3,2304));
        assertThrows(IllegalArgumentException.class,()->TradeMath.total(100,0,2304));
        assertThrows(IllegalArgumentException.class,()->TradeMath.total(100,2305,2304));
        assertThrows(ArithmeticException.class,()->TradeMath.total(Long.MAX_VALUE,2,2304));
    }
    @Test void damageReducesSellPayoutWithoutCreatingExtraMoney(){
        assertEquals(100,TradeMath.damagedSell(100,0,100));assertEquals(50,TradeMath.damagedSell(100,50,100));assertEquals(0,TradeMath.damagedSell(100,100,100));
    }
    @Test void saleCooldownAndTierSurviveRestart()throws Exception{
        ShopCatalog c=defaults();UUID id=UUID.randomUUID();Path file=temporary.resolve("tiers.json");
        PlayerTiers tiers=new PlayerTiers(file);tiers.assign(id,"diamond",c);tiers.reserve(id,1000000);
        PlayerTiers restarted=new PlayerTiers(file);assertEquals("diamond",restarted.tier(id,c));assertEquals(45000,restarted.remaining(id,c,1000000));assertEquals(0,restarted.remaining(id,c,1045001));
    }
    @Test void cancelingFailedSaleRestoresCooldown()throws Exception{
        var c=defaults();var id=UUID.randomUUID();var tiers=new PlayerTiers(temporary.resolve("tiers.json"));long previous=tiers.reserve(id,1000000);tiers.cancel(id,previous);assertEquals(0,tiers.remaining(id,c,1000000));
    }
    @Test void tierStateWriteFailureDoesNotAssignNewTier()throws Exception{
        var c=defaults();var id=UUID.randomUUID();Path file=temporary.resolve("tiers.json");var tiers=new PlayerTiers(file);Files.createDirectory(temporary.resolve("tiers.json.tmp"));
        assertThrows(java.io.IOException.class,()->tiers.assign(id,"diamond",c));assertEquals("iron",tiers.tier(id,c));
    }
}
