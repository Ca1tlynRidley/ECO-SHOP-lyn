package dev.lyn.shop.auction;

import dev.lyn.shop.TradeMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/** Plan the entire edit first, then recheck and persist it inside the auction journal. No ground drops. */
public final class AuctionInventory {
    private AuctionInventory() {}
    public record Plan(ItemStack[] before,ItemStack[] after) {
        public void apply(ServerPlayer player)throws IOException{
            for(int i=0;i<36;i++)if(!same(player.getInventory().getItem(i),before[i]))
                throw new IllegalStateException("Inventory changed during auction operation");
            for(int i=0;i<36;i++)player.getInventory().setItem(i,after[i].copy());
            player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
            player.level().getServer().getPlayerList().save(player);
            // Vanilla logs some save failures instead of throwing. Verify changed inventory slots on disk.
            var path=player.level().getServer().getWorldPath(LevelResource.ROOT).resolve("playerdata").resolve(player.getUUID()+".dat");
            var tag=NbtIo.readCompressed(path,NbtAccounter.create(64L*1024*1024));
            verifySaved(player.registryAccess(),tag,before,after);
            try(var channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.force(true);}
        }
    }
    public static void verifySaved(net.minecraft.core.HolderLookup.Provider registries,CompoundTag data,ItemStack[] before,ItemStack[] after)throws IOException{
        var entries=ItemStackWithSlot.CODEC.listOf().parse(registries.createSerializationContext(NbtOps.INSTANCE),data.getListOrEmpty("Inventory")).getOrThrow();
        var saved=new ItemStack[36];java.util.Arrays.fill(saved,ItemStack.EMPTY);
        for(var entry:entries)if(entry.slot()>=0&&entry.slot()<36){if(!saved[entry.slot()].isEmpty())throw new IOException("Duplicate saved inventory slot");saved[entry.slot()]=entry.stack();}
        for(int i=0;i<36;i++)if(!same(before[i],after[i])&&!same(saved[i],after[i]))throw new IOException("Player inventory save could not be verified at slot "+i);
    }
    public static Plan take(ServerPlayer player,int slot,ItemStack expected,int count){
        if(slot<0||slot>=36||count<1||count>expected.getCount()||!same(player.getInventory().getItem(slot),expected))
            throw new IllegalArgumentException("Your held item changed. Reopen the listing screen.");
        var before=snapshot(player);var after=copy(before);after[slot]=expected.copyWithCount(expected.getCount()-count);return new Plan(before,after);
    }
    public static Plan give(ServerPlayer player,ItemStack stack){
        var before=snapshot(player);var counts=new int[36];var compatible=new boolean[36];
        for(int i=0;i<36;i++){counts[i]=before[i].getCount();compatible[i]=ItemStack.isSameItemSameComponents(before[i],stack);}
        int[] placed=TradeMath.placement(counts,compatible,stack.getMaxStackSize(),stack.getCount());
        if(placed==null)throw new IllegalArgumentException("Not enough inventory space. The item is still in your collection inbox.");
        var after=copy(before);for(int i=0;i<36;i++)if(placed[i]!=counts[i])after[i]=stack.copyWithCount(placed[i]);return new Plan(before,after);
    }
    public static boolean same(ItemStack a,ItemStack b){return a.getCount()==b.getCount()&&ItemStack.isSameItemSameComponents(a,b);}
    private static ItemStack[] snapshot(ServerPlayer player){var stacks=new ItemStack[36];for(int i=0;i<36;i++)stacks[i]=player.getInventory().getItem(i).copy();return stacks;}
    private static ItemStack[] copy(ItemStack[] stacks){var copy=new ItemStack[stacks.length];for(int i=0;i<stacks.length;i++)copy[i]=stacks[i].copy();return copy;}
}
