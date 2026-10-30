package dev.lyn.shop.auction;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lyn.eco.api.EcoApi;
import dev.lyn.eco.api.Money;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.UUID;

public final class AuctionModule {
    private static AuctionModule instance;
    private AuctionHouse house;
    private int ticks;
    private long retryAfter;
    private final Path configFile=FabricLoader.getInstance().getConfigDir().resolve("shop-mod-lyn/auctions.json");
    public static void register(){instance=new AuctionModule();instance.registerEvents();}
    public static AuctionHouse house(){if(instance==null||instance.house==null)throw new IllegalStateException("Auction house is unavailable. Check the server startup log.");return instance.house;}
    public static void open(ServerPlayer player,AuctionMenu.Page page){open(player,page,"");}
    public static void open(ServerPlayer player,AuctionMenu.Page page,String query){
        var house=house();house.available();house.ensure(player.getUUID(),player.getScoreboardName());
        player.openMenu(new SimpleMenuProvider((id,inventory,viewer)->new AuctionMenu(id,inventory,player,house,page,query),Component.literal("Auction House | "+page.title)));
    }
    private void start(MinecraftServer server)throws Exception{
        var config=AuctionConfig.load(configFile);
        var store=new AuctionStore(server.getWorldPath(LevelResource.ROOT).resolve("shop-mod-lyn/auctions.json"));
        house=new AuctionHouse(store,EcoApi.get(),config,System::currentTimeMillis);house.expire();
        LoggerFactory.getLogger("SHOP-MOD-lyn").info("Auction house loaded: {} listings, {} collection entries; ownership uses server player UUIDs",house.book().listings().size(),house.book().mail().size());
    }
    private void registerEvents(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try{start(server);}catch(Exception e){house=null;LoggerFactory.getLogger("SHOP-MOD-lyn").error("Auction house failed to load. Existing auction data was not reset.",e);}});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->house=null);
        ServerTickEvents.END_SERVER_TICK.register(server->{if(++ticks%20!=0||house==null||System.currentTimeMillis()<retryAfter)return;
            try{house.expire();}catch(Exception e){retryAfter=System.currentTimeMillis()+30_000;LoggerFactory.getLogger("SHOP-MOD-lyn").error("Auction expiry update failed; it will be retried without charging players",e);}});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{if(house==null)return;var player=handler.getPlayer();
            int count=house.inbox(player.getUUID()).size();if(count>0)player.sendSystemMessage(Component.literal("Auction House: "+count+" collection entries are waiting. Use /ah inbox."));});
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->{
            var root=Commands.literal("ah").executes(ctx->guard(ctx.getSource(),()->open(ctx.getSource().getPlayerOrException(),AuctionMenu.Page.BUY_NOW)));
            root.then(Commands.literal("bids").executes(ctx->guard(ctx.getSource(),()->open(ctx.getSource().getPlayerOrException(),AuctionMenu.Page.BIDDING))));
            root.then(Commands.literal("mine").executes(ctx->guard(ctx.getSource(),()->open(ctx.getSource().getPlayerOrException(),AuctionMenu.Page.MINE))));
            root.then(Commands.literal("inbox").executes(ctx->guard(ctx.getSource(),()->open(ctx.getSource().getPlayerOrException(),AuctionMenu.Page.INBOX))));
            root.then(Commands.literal("search").then(Commands.argument("query",StringArgumentType.greedyString()).executes(ctx->guard(ctx.getSource(),()->open(ctx.getSource().getPlayerOrException(),AuctionMenu.Page.BUY_NOW,StringArgumentType.getString(ctx,"query"))))));
            for(String command:new String[]{"sell","auction"}){
                var kind=command.equals("sell")?AuctionStore.Kind.BUY_NOW:AuctionStore.Kind.BIDDING;
                root.then(Commands.literal(command).then(Commands.argument("price",StringArgumentType.word())
                    .executes(ctx->guard(ctx.getSource(),()->create(ctx.getSource().getPlayerOrException(),kind,Money.parse(StringArgumentType.getString(ctx,"price")),0)))
                    .then(Commands.argument("hours",IntegerArgumentType.integer(1,720)).executes(ctx->guard(ctx.getSource(),()->create(ctx.getSource().getPlayerOrException(),kind,Money.parse(StringArgumentType.getString(ctx,"price")),IntegerArgumentType.getInteger(ctx,"hours")))))));
            }
            root.then(Commands.literal("bid").then(Commands.argument("listing",StringArgumentType.word()).then(Commands.argument("amount",StringArgumentType.word()).executes(ctx->guard(ctx.getSource(),()->{
                var player=ctx.getSource().getPlayerOrException();UUID id=UUID.fromString(StringArgumentType.getString(ctx,"listing"));long amount=Money.parse(StringArgumentType.getString(ctx,"amount"));
                open(player,AuctionMenu.Page.BIDDING);((AuctionMenu)player.containerMenu).showListing(id,amount);
            })))));
            root.then(Commands.literal("help").executes(ctx->guard(ctx.getSource(),()->reply(ctx.getSource(),"/ah: buy now | /ah bids: auctions | /ah mine | /ah inbox | /ah sell <total price> [hours] | /ah auction <starting total> [hours] | /ah search <text>. Hold an item to list it; prices are for the whole selected bundle."))));
            dispatcher.register(root);
            var admin=Commands.literal("ahadmin").requires(s->s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
            admin.then(Commands.literal("reload").executes(ctx->guard(ctx.getSource(),()->{
                if(house==null)start(ctx.getSource().getServer());else{house.available();house.configure(AuctionConfig.load(configFile));}
                for(var player:ctx.getSource().getServer().getPlayerList().getPlayers())if(player.containerMenu instanceof AuctionMenu)player.closeContainer();
                reply(ctx.getSource(),"Auction configuration reloaded.");
            })));
            admin.then(Commands.literal("cancel").then(Commands.argument("listing",StringArgumentType.word()).executes(ctx->guard(ctx.getSource(),()->{
                var house=house();var listing=house.listing(UUID.fromString(StringArgumentType.getString(ctx,"listing")));house.cancel(listing.seller(),listing.id(),true);reply(ctx.getSource(),"Listing cancelled. Item and any bid refund are in their owners' collection inboxes.");
            }))));
            admin.then(Commands.literal("pending").executes(ctx->guard(ctx.getSource(),()->{
                var pending=house().pending();reply(ctx.getSource(),pending==null?"No unfinished auction operation.":"Review "+pending.id()+" | "+pending.kind()+" | actor="+pending.actor()+" ("+pending.actorName()+") | "+pending.details()+". Check economy and player data before /ahadmin resolve.");
            })));
            admin.then(Commands.literal("reassign").then(Commands.argument("oldUuid",StringArgumentType.word()).then(Commands.argument("player",EntityArgument.player()).executes(ctx->guard(ctx.getSource(),()->{
                var player=EntityArgument.getPlayer(ctx,"player");int moved=house().reassign(UUID.fromString(StringArgumentType.getString(ctx,"oldUuid")),player.getUUID(),player.getScoreboardName());
                for(var viewer:ctx.getSource().getServer().getPlayerList().getPlayers())if(viewer.containerMenu instanceof AuctionMenu)viewer.closeContainer();
                reply(ctx.getSource(),"Reassigned "+moved+" auction records to "+player.getScoreboardName()+" ("+player.getUUID()+"). Existing ECO balances were not moved.");
            })))));
            var resolve=Commands.argument("operation",StringArgumentType.word());
            for(String choice:new String[]{"commit","abort"})resolve.then(Commands.literal(choice).executes(ctx->guard(ctx.getSource(),()->{
                house().resolve(UUID.fromString(StringArgumentType.getString(ctx,"operation")),choice.equals("commit"));reply(ctx.getSource(),"Auction operation resolved as "+choice+". No external payment or inventory edit was replayed.");
            })));
            admin.then(Commands.literal("resolve").then(resolve));dispatcher.register(admin);
        });
    }
    private static void create(ServerPlayer player,AuctionStore.Kind kind,long price,int hours){
        open(player,AuctionMenu.Page.CREATE);((AuctionMenu)player.containerMenu).configureListing(kind,price,hours);
    }
    @FunctionalInterface private interface Work{void run()throws Exception;}
    private static int guard(CommandSourceStack source,Work work){try{work.run();return 1;}catch(Exception e){reply(source,"Auction House: "+(e.getMessage()==null?"Action failed.":e.getMessage()));return 0;}}
    private static void reply(CommandSourceStack source,String text){source.sendSystemMessage(Component.literal(text));}
}
