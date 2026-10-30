package dev.lyn.shop;

import dev.lyn.eco.api.EcoApi;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;

/** A vanilla chest menu. Player items stay in their inventory until a confirmed sale. */
public final class ShopMenu extends ChestMenu {
    private final ServerPlayer owner;
    private final ShopCatalog catalog;
    private final Trades trades;
    private final PlayerTiers tiers;
    private final SimpleContainer display;
    private final Map<Integer,Runnable> buttons=new HashMap<>();
    private List<ShopCatalog.Product> listing=List.of();
    private int page;
    private ShopCatalog.Product selected;
    private int quantity=1;
    private long nextClick;
    private boolean busy;
    public ShopMenu(int id,Inventory inventory,ServerPlayer owner,ShopCatalog catalog,Trades trades,PlayerTiers tiers,boolean selling,String search){
        this(id,inventory,owner,catalog,trades,tiers,new SimpleContainer(54));
        if(selling)sellList();else if(search!=null&&!search.isBlank())showProducts(catalog.items().values().stream().filter(p->p.id().contains(search.toLowerCase(Locale.ROOT).replace(' ','_'))).sorted(Comparator.comparing(ShopCatalog.Product::id)).toList(),0);else home();
    }
    private ShopMenu(int id,Inventory inventory,ServerPlayer owner,ShopCatalog catalog,Trades trades,PlayerTiers tiers,SimpleContainer display){
        super(MenuType.GENERIC_9x6,id,inventory,display,6);
        this.owner=owner;this.catalog=catalog;this.trades=trades;this.tiers=tiers;this.display=display;
    }
    private void clear(){buttons.clear();for(int i=0;i<54;i++)display.setItem(i,label("minecraft:gray_stained_glass_pane"," "));}
    private void put(int slot,String id,String name,Runnable action,String... lore){display.setItem(slot,label(id,name,lore));if(action!=null)buttons.put(slot,action);}
    private static ItemStack label(String id,String name,String... lore){ItemStack stack=Trades.fresh(id);stack.set(DataComponents.CUSTOM_NAME,Component.literal(name));stack.set(DataComponents.LORE,new ItemLore(Arrays.stream(lore).map(Component::literal).map(c->(Component)c).toList()));return stack;}
    private String money(long amount){return EcoApi.get().format(amount);}
    private void finish(){put(53,"minecraft:barrier","Close",owner::closeContainer);put(46,"minecraft:emerald","Balance: "+money(EcoApi.get().balance(owner.getUUID())),null,"ECO-API-lyn");broadcastChanges();}
    private void home(){
        clear();String[] categories={"ingredients","blocks","gear","food","redstone","nature","utility","new"};
        String[] icons={"iron_ingot","stone","diamond_sword","apple","redstone","oak_sapling","crafting_table","copper_chest"};
        int[] slots={10,12,14,16,28,30,32,34};
        for(int i=0;i<categories.length;i++){String category=categories[i];put(slots[i],"minecraft:"+icons[i],pretty(category),()->showProducts(catalog.category(category),0),catalog.category(category).size()+" items","Click to browse");}
        put(49,"minecraft:gold_ingot","Sell your items",this::sellList,"Your items remain in your inventory","until you confirm a sale.");finish();
    }
    private void showProducts(List<ShopCatalog.Product> products,int requested){
        listing=products;page=Math.clamp(requested,0,Math.max(0,(products.size()-1)/45));clear();
        for(int i=page*45;i<Math.min(products.size(),(page+1)*45);i++){
            var product=products.get(i);
            put(i-page*45,product.id(),pretty(product.id().substring(10)),()->{selected=product;quantity=1;buyDetails();},"Buy: "+money(product.buy())+" each","Sell: "+money(product.sell())+" each","Click to choose quantity");
        }
        put(45,"minecraft:arrow","Categories",this::home);
        if(page>0)put(48,"minecraft:arrow","Previous page",()->showProducts(listing,page-1));
        put(49,"minecraft:paper","Page "+(page+1)+" / "+Math.max(1,(products.size()+44)/45),null,products.size()+" matching items");
        if((page+1)*45<products.size())put(50,"minecraft:arrow","Next page",()->showProducts(listing,page+1));finish();
    }
    private void buyDetails(){
        clear();long total=TradeMath.total(selected.buy(),quantity,catalog.maxPurchase());
        put(22,selected.id(),pretty(selected.id().substring(10))+" x "+quantity,null,"Total: "+money(total),"Unit buy: "+money(selected.buy()),"Unit sell: "+money(selected.sell()));
        int[] slots={19,20,21,23,24,25},deltas={-64,-16,-1,1,16,64};
        for(int i=0;i<slots.length;i++){int delta=deltas[i];put(slots[i],delta>0?"minecraft:lime_stained_glass_pane":"minecraft:red_stained_glass_pane",(delta>0?"+":"")+delta,()->{quantity=Math.clamp(quantity+delta,1,catalog.maxPurchase());buyDetails();});}
        put(31,"minecraft:lime_stained_glass","Confirm purchase",()->attempt(()->{trades.buy(owner,catalog,selected,quantity);owner.closeContainer();}),"Pay "+money(total),"No charge if your inventory is full.");
        put(45,"minecraft:arrow","Back",()->showProducts(listing,page));finish();
    }
    public void sellList(){
        clear();
        for(int slot=0;slot<36;slot++){
            int inventorySlot=slot;ItemStack stack=owner.getInventory().getItem(slot);if(stack.isEmpty())continue;
            long unit=Trades.sellUnit(stack,catalog);
            if(unit==0){put(slot,"minecraft:red_stained_glass_pane","Not sellable: "+pretty(Trades.id(stack).substring(10)),null,"Excluded, customized, enchanted, or contains data.","This item stays in your inventory.");continue;}
            ItemStack preview=label(Trades.id(stack),pretty(Trades.id(stack).substring(10)),"Sell: "+money(unit)+" each","Stack value: "+money(Math.multiplyExact(unit,stack.getCount())),"Click to review this stack");
            preview.setCount(Math.min(stack.getCount(),preview.getMaxStackSize()));display.setItem(slot,preview);
            buttons.put(slot,()->saleDetails(inventorySlot,stack.getCount()));
        }
        String tier=tiers.tier(owner.getUUID(),catalog);long wait=tiers.remaining(owner.getUUID(),catalog,System.currentTimeMillis());
        put(40,"minecraft:clock",pretty(tier)+" sale cooldown",null,catalog.sellCooldownSeconds().get(tier)+" seconds between successful sales",wait==0?"Ready to sell":"Ready in "+((wait+999)/1000)+" seconds");
        put(45,"minecraft:arrow","Shop categories",this::home);
        put(49,"minecraft:lime_stained_glass","Review selling all eligible items",()->saleDetails(-1,Integer.MAX_VALUE),"Confirmation required","Protected/custom items are skipped.");finish();
    }
    public void saleDetails(int slot,int maximum){
        Trades.Quote quote=Trades.quote(owner,catalog,slot,maximum);clear();
        put(22,"minecraft:gold_ingot","Sale quote: "+money(quote.total()),null,quote.quantity()+" items across "+quote.lines().size()+" stacks","No items have been moved or removed.");
        put(31,"minecraft:lime_stained_glass","Confirm sale",()->attempt(()->{trades.sell(owner,catalog,quote);owner.closeContainer();}),"Receive "+money(quote.total()),"A successful sale starts your cooldown.");
        put(45,"minecraft:arrow","Cancel / back",this::sellList);finish();
    }
    @FunctionalInterface private interface Work{void run()throws Exception;}
    private void attempt(Work work){if(busy)return;busy=true;try{work.run();}catch(Exception e){owner.sendSystemMessage(Component.literal("SHOP-MOD-lyn: "+(e.getMessage()==null?"Trade failed":e.getMessage())));sendAllDataToRemote();}finally{busy=false;}}
    private static String pretty(String text){String[] words=text.split("_");StringJoiner result=new StringJoiner(" ");for(String word:words)if(!word.isEmpty())result.add(Character.toUpperCase(word.charAt(0))+word.substring(1));return result.toString();}
    @Override public boolean stillValid(Player player){return player==owner&&owner.isAlive();}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public void clicked(int slot,int button,ContainerInput input,Player player){
        if(player!=owner)return;sendAllDataToRemote();
        if(input!=ContainerInput.PICKUP||button<0||button>1||busy)return;
        long now=System.nanoTime();if(now<nextClick)return;nextClick=now+200_000_000L;
        Runnable action=buttons.get(slot);
        if(action!=null)try{action.run();}catch(RuntimeException e){owner.sendSystemMessage(Component.literal("SHOP-MOD-lyn: "+e.getMessage()));sendAllDataToRemote();}
    }
}
