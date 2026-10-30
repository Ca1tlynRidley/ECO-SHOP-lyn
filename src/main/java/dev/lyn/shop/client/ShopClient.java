package dev.lyn.shop.client;

import dev.lyn.shop.network.ShopNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;

public final class ShopClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        var requirement=ShopNetworking.CLIENT_REQUIREMENT;
        ClientConfigurationNetworking.registerGlobalReceiver(requirement.probeType(),(payload,context)->
            context.responseSender().sendPacket(requirement.reply(payload.nonce())));
    }
}
