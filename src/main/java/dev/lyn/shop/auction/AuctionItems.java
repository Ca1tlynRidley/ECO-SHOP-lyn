package dev.lyn.shop.auction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import dev.lyn.shop.Trades;
import java.io.*;
import java.util.Base64;

/** Registry-aware binary NBT preserves numeric tag types and all persistent modded components. */
public final class AuctionItems {
    private AuctionItems() {}
    public static AuctionStore.Lot encode(HolderLookup.Provider registries,ItemStack stack,int limit)throws IOException{
        if(stack.isEmpty()||stack.getCount()>stack.getMaxStackSize())throw new IllegalArgumentException("Hold a valid item stack first.");
        var tag=ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),stack).getOrThrow();
        if(!(tag instanceof CompoundTag compound))throw new IOException("Item codec did not produce an NBT compound");
        var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(compound,bytes);
        if(bytes.size()>limit)throw new IllegalArgumentException("This item's saved data exceeds the auction size limit.");
        String name=stack.getHoverName().getString();if(name.length()>512)name=name.substring(0,512);
        var lot=new AuctionStore.Lot(Base64.getEncoder().encodeToString(bytes.toByteArray()),Trades.id(stack),name,stack.getCount());
        ItemStack decoded=decode(registries,lot,limit);
        if(decoded.getCount()!=stack.getCount()||!ItemStack.isSameItemSameComponents(stack,decoded))
            throw new IllegalArgumentException("This item's mod does not save all of its components. It cannot safely be auctioned.");
        return lot;
    }
    public static ItemStack decode(HolderLookup.Provider registries,AuctionStore.Lot lot,int limit)throws IOException{
        byte[] bytes=Base64.getDecoder().decode(lot.data());
        if(bytes.length>limit)throw new IOException("Auction item exceeds size limit");
        var tag=NbtIo.readCompressed(new ByteArrayInputStream(bytes),NbtAccounter.create(limit));
        var stack=ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),tag).getOrThrow();
        ItemStack.validateStrict(stack).getOrThrow();
        if(stack.isEmpty()||!Trades.id(stack).equals(lot.itemId())||stack.getCount()!=lot.count())throw new IOException("Auction item metadata does not match saved stack");
        return stack;
    }
}
