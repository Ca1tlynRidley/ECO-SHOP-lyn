package dev.lyn.shop.network;

import net.fabricmc.api.ModInitializer;

/** Shared protocol only: safe to load on clients without the economy mod. */
public final class ShopNetworking implements ModInitializer {
    public static final ClientRequirement CLIENT_REQUIREMENT=ClientRequirement.forMod("shop_mod_lyn","SHOP-MOD-lyn");
    @Override public void onInitialize(){CLIENT_REQUIREMENT.registerCommon();}
}
