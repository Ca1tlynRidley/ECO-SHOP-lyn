package dev.lyn.shop;

import dev.lyn.eco.api.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.nio.file.*;
import java.time.Instant;
import org.slf4j.LoggerFactory;

public final class Trades {
    public record Sale(int slot,ItemStack expected,int count,long unitPrice) {}
    public record Quote(List<Sale> lines,long total,int quantity) { public Quote { lines=List.copyOf(lines); } }
    private final PlayerTiers tiers;
    private final Path incidents;
    public Trades(PlayerTiers tiers,Path incidents){this.tiers=tiers;this.incidents=incidents;}
    public static void ensure(ServerPlayer player){EcoApi.get().ensureAccount(player.getUUID(),player.getScoreboardName());}
    public static String id(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    public static ItemStack fresh(String id){return new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow());}
    public static long sellUnit(ItemStack stack,ShopCatalog catalog){
        if(stack.isEmpty())return 0;
        var product=catalog.items().get(id(stack));if(product==null)return 0;
        ItemStack plain=fresh(product.id()),normalized=stack.copyWithCount(1);
        if(normalized.isDamageableItem())normalized.setDamageValue(0);
        if(!ItemStack.isSameItemSameComponents(plain,normalized))return 0;
        return TradeMath.damagedSell(product.sell(),stack.getDamageValue(),stack.isDamageableItem()?stack.getMaxDamage():0);
    }
    public static Quote quote(ServerPlayer player,ShopCatalog catalog,int onlySlot,int maximum){
        List<Sale> lines=new ArrayList<>();long total=0;int count=0;
        for(int slot=0;slot<36;slot++){
            if(onlySlot>=0&&slot!=onlySlot)continue;
            ItemStack stack=player.getInventory().getItem(slot);long unit=sellUnit(stack,catalog);if(unit==0)continue;
            int amount=Math.min(stack.getCount(),maximum);
            if(amount<=0)continue;
            total=Math.addExact(total,Math.multiplyExact(unit,amount));count=Math.addExact(count,amount);
            lines.add(new Sale(slot,stack.copy(),amount,unit));
        }
        return new Quote(lines,total,count);
    }
    public void buy(ServerPlayer player,ShopCatalog catalog,ShopCatalog.Product product,int quantity){
        ensure(player);
        long cost=TradeMath.total(product.buy(),quantity,catalog.maxPurchase());
        ItemStack template=fresh(product.id());
        ItemStack[] before=snapshot(player);int[] counts=new int[36];boolean[] compatible=new boolean[36];
        for(int slot=0;slot<36;slot++){counts[slot]=before[slot].getCount();compatible[slot]=ItemStack.isSameItemSameComponents(before[slot],template);}
        int[] after=TradeMath.placement(counts,compatible,template.getMaxStackSize(),quantity);
        if(after==null)throw new IllegalArgumentException("Your inventory does not have enough space. No money was charged.");
        var payment=EcoApi.get().withdraw(player.getUUID(),cost,"shop:buy:"+product.id()+":"+quantity);
        if(!payment.success())throw new IllegalArgumentException(explain(payment.status()));
        try{
            for(int slot=0;slot<36;slot++)if(after[slot]!=counts[slot])player.getInventory().setItem(slot,template.copyWithCount(after[slot]));
            persistPlayer(player);
        }catch(RuntimeException failure){
            restore(player,before);
            var refund=EcoApi.get().deposit(player.getUUID(),cost,"shop:refund:"+payment.transactionId());
            incident(player,"purchase delivery failure; payment="+payment.transactionId()+" refund="+refund.status()+" cents="+cost,failure);
            throw new IllegalStateException(refund.success()?"Purchase failed; your payment was refunded.":"Purchase failed and refund needs administrator attention. Reference: "+payment.transactionId());
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Bought "+quantity+" x "+product.id().substring(10)+" for "+EcoApi.get().format(cost)));
    }
    public void sell(ServerPlayer player,ShopCatalog catalog,Quote quote)throws java.io.IOException{
        ensure(player);
        if(quote.lines().isEmpty())throw new IllegalArgumentException("No eligible items were selected.");
        long remaining=tiers.remaining(player.getUUID(),catalog,System.currentTimeMillis());
        if(remaining>0)throw new IllegalArgumentException("You can sell again in "+((remaining+999)/1000)+" seconds.");
        for(Sale line:quote.lines()){
            ItemStack actual=player.getInventory().getItem(line.slot());
            if(!ItemStack.isSameItemSameComponents(actual,line.expected())||actual.getCount()!=line.expected().getCount()||actual.getCount()<line.count())
                throw new IllegalArgumentException("Your inventory changed. Reopen /sell to get a fresh quote.");
            if(sellUnit(actual,catalog)!=line.unitPrice())throw new IllegalArgumentException("Prices changed. Reopen /sell.");
        }
        ItemStack[] before=snapshot(player);
        long prior=tiers.reserve(player.getUUID(),System.currentTimeMillis());
        var payment=EcoApi.get().deposit(player.getUUID(),quote.total(),"shop:sell:"+quote.quantity());
        if(!payment.success()){tiers.cancel(player.getUUID(),prior);throw new IllegalArgumentException(explain(payment.status()));}
        try{
            for(Sale line:quote.lines())player.getInventory().setItem(line.slot(),line.expected().copyWithCount(line.expected().getCount()-line.count()));
            persistPlayer(player);
        }catch(RuntimeException failure){
            restore(player,before);
            var reversal=EcoApi.get().withdraw(player.getUUID(),quote.total(),"shop:sell-reversal:"+payment.transactionId());
            if(reversal.success())tiers.cancel(player.getUUID(),prior);
            incident(player,"sale inventory failure; payment="+payment.transactionId()+" reversal="+reversal.status()+" cents="+quote.total(),failure);
            throw new IllegalStateException("Sale failed; contact an administrator with reference "+payment.transactionId());
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Sold "+quote.quantity()+" items for "+EcoApi.get().format(quote.total())));
    }
    private static ItemStack[] snapshot(ServerPlayer player){ItemStack[] result=new ItemStack[36];for(int i=0;i<36;i++)result[i]=player.getInventory().getItem(i).copy();return result;}
    private static void restore(ServerPlayer player,ItemStack[] before){for(int i=0;i<36;i++)player.getInventory().setItem(i,before[i]);player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();}
    private static void persistPlayer(ServerPlayer player){player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();player.level().getServer().getPlayerList().save(player);}
    private void incident(ServerPlayer player,String text,Exception failure){
        LoggerFactory.getLogger("SHOP-MOD-lyn").error("{}: {}",player.getUUID(),text,failure);
        try{Files.createDirectories(incidents.getParent());Files.writeString(incidents,Instant.now()+" "+player.getUUID()+" "+text+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}
    }
    public static String explain(EconomyService.Status status){return switch(status){
        case INSUFFICIENT_FUNDS->"You do not have enough money.";
        case BALANCE_LIMIT->"Your balance would exceed the economy limit.";
        case STORAGE_ERROR->"Economy storage failed. This trade was not applied.";
        default->"Trade rejected: "+status;
    };}
}
