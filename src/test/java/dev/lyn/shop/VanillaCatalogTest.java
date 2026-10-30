package dev.lyn.shop;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.*;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class VanillaCatalogTest {
    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var lookup=net.minecraft.data.registries.VanillaRegistries.createLookup();
        for(var pending:BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup))pending.apply();
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
}
