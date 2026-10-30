package dev.lyn.shop;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/** Enforce the economy dependency on the dedicated server, never on a player's client. */
public final class ShopServer implements DedicatedServerModInitializer {
    @Override public void onInitializeServer(){
        var eco=FabricLoader.getInstance().getModContainer("eco_api_lyn");
        if(eco.isEmpty()||!supportsEconomy(eco.get().getMetadata().getVersion()))
            throw new IllegalStateException("SHOP-MOD-lyn requires ECO-API-lyn 1.2.0 or newer on the server. Install the economy JAR in the server's mods folder. Players do not need ECO.");
        new ShopMod().onInitialize();
    }
    public static boolean supportsEconomy(Version version){
        try{return VersionPredicate.parse(">=1.2.0").test(version);}
        catch(VersionParsingException e){throw new IllegalStateException("Invalid economy dependency version",e);}
    }
}
