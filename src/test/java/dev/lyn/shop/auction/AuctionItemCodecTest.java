package dev.lyn.shop.auction;

import dev.lyn.shop.MinecraftTestRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.enchantment.*;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionItemCodecTest {
    @BeforeAll static void bootstrap(){MinecraftTestRegistry.init();}
    private static ItemStack roundTrip(ItemStack original)throws Exception{
        var lot=AuctionItems.encode(MinecraftTestRegistry.lookup,original,1024*1024);
        var restored=AuctionItems.decode(MinecraftTestRegistry.lookup,lot,1024*1024);
        assertEquals(original.getCount(),restored.getCount());assertTrue(ItemStack.isSameItemSameComponents(original,restored));return restored;
    }
    @Test void nonVanillaRegistryIdAndPersistentModdedComponentSurviveExactly()throws Exception{
        var stack=new ItemStack(MinecraftTestRegistry.modItem,3);stack.set(MinecraftTestRegistry.modComponent,"https://example.invalid/custom-track.ogg");
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Custom mod music disc"));
        var lot=AuctionItems.encode(MinecraftTestRegistry.lookup,stack,1024*1024);assertEquals("auction_test:custom_disc",lot.itemId());
        var restored=roundTrip(stack);assertEquals("https://example.invalid/custom-track.ogg",restored.get(MinecraftTestRegistry.modComponent));
    }
    @Test void typedCustomNbtArraysAndNumbersAreNotFlattenedToJson()throws Exception{
        var stack=new ItemStack(MinecraftTestRegistry.modItem);var data=new CompoundTag();
        data.putByte("byte",(byte)1);data.putInt("int",1);data.putLongArray("longs",new long[]{1,Long.MAX_VALUE});data.putByteArray("bytes",new byte[]{-1,2,3});
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));assertEquals(stack.get(DataComponents.CUSTOM_DATA),roundTrip(stack).get(DataComponents.CUSTOM_DATA));
    }
    @Test void enchantedNamedDamagedGearRetainsItsComponents()throws Exception{
        var stack=new ItemStack(Items.DIAMOND_SWORD);stack.setDamageValue(123);stack.set(DataComponents.CUSTOM_NAME,Component.literal("A treasured sword"));
        var enchantments=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(MinecraftTestRegistry.lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS),5);
        stack.set(DataComponents.ENCHANTMENTS,enchantments.toImmutable());roundTrip(stack);
    }
    @Test void filledContainersRetainTheirNestedModdedItems()throws Exception{
        var nested=new ItemStack(MinecraftTestRegistry.modItem,2);nested.set(MinecraftTestRegistry.modComponent,"saved mod payload");
        var container=new ItemStack(Items.SHULKER_BOX);container.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(nested,new ItemStack(Items.DIAMOND,5))));roundTrip(container);
    }
    @Test void tamperedSavedMetadataAndOversizedItemsAreRejectedWithoutChangingTheOriginal()throws Exception{
        var stack=new ItemStack(MinecraftTestRegistry.modItem);var lot=AuctionItems.encode(MinecraftTestRegistry.lookup,stack,1024*1024);
        var wrongCount=new AuctionStore.Lot(lot.data(),lot.itemId(),lot.name(),2);assertThrows(IOException.class,()->AuctionItems.decode(MinecraftTestRegistry.lookup,wrongCount,1024*1024));
        stack.set(MinecraftTestRegistry.modComponent,UUID.randomUUID().toString().repeat(100));var before=stack.copy();
        assertThrows(IllegalArgumentException.class,()->AuctionItems.encode(MinecraftTestRegistry.lookup,stack,32));assertTrue(ItemStack.isSameItemSameComponents(before,stack));
    }
    @Test void failedPlayerSaveIsDetectedForBothListingRemovalAndItemCollection()throws Exception{
        ItemStack[] before=empty(),after=empty();before[0]=new ItemStack(Items.DIAMOND,3);after[0]=new ItemStack(Items.DIAMOND,2);
        assertThrows(IOException.class,()->AuctionInventory.verifySaved(MinecraftTestRegistry.lookup,saved(before),before,after));
        AuctionInventory.verifySaved(MinecraftTestRegistry.lookup,saved(after),before,after);
        ItemStack[] claimBefore=empty(),claimAfter=empty();claimAfter[4]=new ItemStack(MinecraftTestRegistry.modItem);
        assertThrows(IOException.class,()->AuctionInventory.verifySaved(MinecraftTestRegistry.lookup,saved(claimBefore),claimBefore,claimAfter));
        AuctionInventory.verifySaved(MinecraftTestRegistry.lookup,saved(claimAfter),claimBefore,claimAfter);
    }
    private static ItemStack[] empty(){var slots=new ItemStack[36];Arrays.fill(slots,ItemStack.EMPTY);return slots;}
    private static CompoundTag saved(ItemStack[] slots){
        var entries=new ArrayList<ItemStackWithSlot>();for(int i=0;i<slots.length;i++)if(!slots[i].isEmpty())entries.add(new ItemStackWithSlot(i,slots[i]));
        var tag=new CompoundTag();tag.put("Inventory",ItemStackWithSlot.CODEC.listOf().encodeStart(MinecraftTestRegistry.lookup.createSerializationContext(NbtOps.INSTANCE),entries).getOrThrow());return tag;
    }
}
