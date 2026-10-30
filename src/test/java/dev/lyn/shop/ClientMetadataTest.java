package dev.lyn.shop;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class ClientMetadataTest {
    @Test void shopClientHasNoHardEconomyDependencyAndServerHasItsOwnInitializer()throws Exception{
        try(var in=getClass().getResourceAsStream("/fabric.mod.json")){
            var mod=JsonParser.parseString(new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("*",mod.get("environment").getAsString());assertEquals("1.3.0",mod.get("version").getAsString());
            assertFalse(mod.getAsJsonObject("depends").has("eco_api_lyn"));
            assertEquals(">=1.2.0",mod.getAsJsonObject("suggests").get("eco_api_lyn").getAsString());
            assertEquals("dev.lyn.shop.network.ShopNetworking",mod.getAsJsonObject("entrypoints").getAsJsonArray("main").get(0).getAsString());
            assertEquals("dev.lyn.shop.ShopServer",mod.getAsJsonObject("entrypoints").getAsJsonArray("server").get(0).getAsString());
            assertEquals("dev.lyn.shop.client.ShopClient",mod.getAsJsonObject("entrypoints").getAsJsonArray("client").get(0).getAsString());
            assertNotNull(getClass().getResource("/dev/lyn/shop/client/ShopClient.class"));
        }
    }
    @Test void dedicatedServerRequiresNewEconomyButClientDoesNot()throws Exception{
        assertFalse(ShopServer.supportsEconomy(net.fabricmc.loader.api.Version.parse("1.1.0")));
        assertTrue(ShopServer.supportsEconomy(net.fabricmc.loader.api.Version.parse("1.2.0")));
        assertTrue(ShopServer.supportsEconomy(net.fabricmc.loader.api.Version.parse("1.2.1")));
    }
    @Test void clientAndSharedNetworkingClassesDoNotReferenceEconomyClasses()throws Exception{
        for(String path:java.util.List.of("dev/lyn/shop/client/ShopClient.class","dev/lyn/shop/network/ShopNetworking.class","dev/lyn/shop/network/ClientRequirement.class","dev/lyn/shop/network/ClientRequirement$Probe.class","dev/lyn/shop/network/ClientRequirement$Reply.class","dev/lyn/shop/network/ClientRequirement$RequiredTask.class","dev/lyn/shop/network/HandshakeSession.class")){
            try(var in=getClass().getResourceAsStream("/"+path)){
                String bytecode=new String(Objects.requireNonNull(in,path).readAllBytes(),StandardCharsets.ISO_8859_1);
                assertFalse(bytecode.contains("dev/lyn/eco/"),path);
                assertFalse(bytecode.contains("dev/lyn/shop/ShopMod"),path);
            }
        }
    }
}
