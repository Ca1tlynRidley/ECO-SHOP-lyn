package dev.lyn.shop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.*;
import java.util.Objects;
import java.nio.file.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VanillaCatalogTest {
    @TempDir Path temporary;
    @BeforeAll static void bootstrap(){
        MinecraftTestRegistry.init();
    }
    private ShopCatalog defaults()throws Exception{try(var input=getClass().getResourceAsStream("/default-prices.json")){return ShopCatalog.parse(new String(Objects.requireNonNull(input).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}
    @Test void everyConfiguredIdExistsAndEveryFreshPurchaseCanBeSold()throws Exception{
        var catalog=defaults();for(var p:catalog.items().values()){
            assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.parse(p.id())),p.id());
            ItemStack bought=Trades.fresh(p.id());assertEquals(p.sell(),Trades.sellUnit(bought,catalog),p.id());
        }
    }
    @Test void eggsSellAndNamedOrFilledItemsAreProtected()throws Exception{
        var c=defaults();var egg=Trades.fresh("minecraft:egg");assertTrue(Trades.sellUnit(egg,c)>0);
        egg.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("My special egg"));assertEquals(0,Trades.sellUnit(egg,c));
    }
    @Test void existingServerCatalogReceivesTotemsAndStillHonorsLaterRemoval()throws Exception{
        String original;
        try(var input=getClass().getResourceAsStream("/default-prices.json")){original=new String(Objects.requireNonNull(input).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        var root=com.google.gson.JsonParser.parseString(original).getAsJsonObject();root.remove("catalogRevision");root.getAsJsonObject("items").remove("minecraft:totem_of_undying");
        root.getAsJsonObject("items").getAsJsonObject("minecraft:diamond").addProperty("buy","5432.10");
        Path file=temporary.resolve("prices.json");Files.writeString(file,root.toString());var store=new CatalogStore(file);store.load();
        assertEquals(30_000,store.current().items().get("minecraft:totem_of_undying").buy());assertEquals(543_210,store.current().items().get("minecraft:diamond").buy());
        assertTrue(Files.exists(temporary.resolve("prices.json.pre-totem.bak")));
        var updated=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();updated.getAsJsonObject("items").remove("minecraft:totem_of_undying");Files.writeString(file,updated.toString());store.load();
        assertFalse(store.current().items().containsKey("minecraft:totem_of_undying"));
    }
}
