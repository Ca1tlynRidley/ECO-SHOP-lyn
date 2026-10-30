package dev.lyn.shop;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lyn.eco.api.EcoApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public final class ShopMod implements ModInitializer {
    private final CatalogStore catalogs=new CatalogStore(FabricLoader.getInstance().getConfigDir().resolve("shop-mod-lyn/prices.json"));
    private PlayerTiers tiers;
    private Trades trades;
    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            try{
                catalogs.load();Path folder=server.getWorldPath(LevelResource.ROOT).resolve("shop-mod-lyn");
                tiers=new PlayerTiers(folder.resolve("player-tiers.json"));trades=new Trades(tiers,folder.resolve("incidents.log"));
                LoggerFactory.getLogger("SHOP-MOD-lyn").info("Loaded {} survival shop items with ECO-API-lyn",catalogs.current().items().size());
            }catch(Exception e){LoggerFactory.getLogger("SHOP-MOD-lyn").error("Shop initialization failed; fix configuration and restart the server",e);}
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->{
            dispatcher.register(Commands.literal("shop").executes(ctx->guard(ctx.getSource(),()->{open(ctx.getSource().getPlayerOrException(),false,null,null);return 1;}))
                .then(Commands.literal("search").then(Commands.argument("query",StringArgumentType.greedyString()).executes(ctx->guard(ctx.getSource(),()->{open(ctx.getSource().getPlayerOrException(),false,StringArgumentType.getString(ctx,"query"),null);return 1;})))));
            dispatcher.register(Commands.literal("sell").executes(ctx->guard(ctx.getSource(),()->{open(ctx.getSource().getPlayerOrException(),true,null,null);return 1;}))
                .then(Commands.literal("hand").executes(ctx->guard(ctx.getSource(),()->{var p=ctx.getSource().getPlayerOrException();open(p,true,null,p.getInventory().getSelectedSlot());return 1;})))
                .then(Commands.literal("all").executes(ctx->guard(ctx.getSource(),()->{open(ctx.getSource().getPlayerOrException(),true,null,-1);return 1;}))));
            dispatcher.register(Commands.literal("shopadmin").requires(s->s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("reload").executes(ctx->guard(ctx.getSource(),()->{
                    if(tiers==null)throw new IllegalStateException("Shop did not initialize; fix the startup error and restart.");
                    catalogs.load();for(var p:ctx.getSource().getServer().getPlayerList().getPlayers())if(p.containerMenu instanceof ShopMenu)p.closeContainer();
                    ctx.getSource().sendSuccess(()->Component.literal("Reloaded "+catalogs.current().items().size()+" shop entries. Invalid configs never replace the active catalog."),true);return 1;
                })))
                .then(Commands.literal("tier").then(Commands.argument("player",StringArgumentType.word()).then(Commands.argument("tier",StringArgumentType.word()).executes(ctx->guard(ctx.getSource(),()->{
                    if(tiers==null)throw new IllegalStateException("Shop is unavailable.");
                    var account=EcoApi.get().findAccount(StringArgumentType.getString(ctx,"player")).orElseThrow(()->new IllegalArgumentException("Use a known player name or UUID."));
                    String tier=StringArgumentType.getString(ctx,"tier");tiers.assign(account.id(),tier,catalogs.current());
                    ctx.getSource().sendSuccess(()->Component.literal(account.name()+" now has shop tier "+tier),true);return 1;
                }))))));
        });
    }
    private void open(ServerPlayer player,boolean sell,String search,Integer saleSlot){
        if(trades==null||tiers==null)throw new IllegalStateException("Shop is unavailable. Check the server log.");
        Trades.ensure(player);ShopCatalog catalog=catalogs.current();
        player.openMenu(new SimpleMenuProvider((id,inventory,viewer)->{
            ShopMenu menu=new ShopMenu(id,inventory,player,catalog,trades,tiers,sell,search);
            if(saleSlot!=null)menu.saleDetails(saleSlot,Integer.MAX_VALUE);return menu;
        },Component.literal(sell?"SHOP-MOD-lyn | Sell":"SHOP-MOD-lyn | Shop")));
    }
    @FunctionalInterface private interface Command{int run()throws Exception;}
    private static int guard(CommandSourceStack source,Command command){try{return command.run();}catch(Exception e){source.sendFailure(Component.literal("SHOP-MOD-lyn: "+e.getMessage()));return 0;}}
}
