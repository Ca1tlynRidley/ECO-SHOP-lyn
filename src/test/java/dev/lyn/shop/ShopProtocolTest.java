package dev.lyn.shop;

import dev.lyn.shop.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ShopProtocolTest {
    @Test void shopOwnsItsProbeAndReplyWithoutEconomyClasses(){
        var req=new ClientRequirement("shop_mod_lyn","SHOP-MOD-lyn","1.3.0");var nonce=UUID.randomUUID();
        assertEquals("shop_mod_lyn",req.probeType().id().getNamespace());
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{req.probeCodec().encode(buffer,req.probe(nonce));assertEquals(req.probe(nonce),req.probeCodec().decode(buffer));
            buffer.clear();req.replyCodec().encode(buffer,req.reply(nonce));assertEquals(req.reply(nonce),req.replyCodec().decode(buffer));}
        finally{buffer.release();}
    }
    @Test void matchingShopClientPassesAndOldVersionsOrWrongNoncesDoNot(){
        var nonce=UUID.randomUUID();var s=new HandshakeSession(1,"1.3.0",nonce);s.start(0);
        assertEquals(HandshakeSession.Result.VERSION_MISMATCH,s.accept(1,"1.2.0",nonce,1));
        assertEquals(HandshakeSession.Result.NONCE_MISMATCH,s.accept(1,"1.3.0",UUID.randomUUID(),1));
        assertFalse(s.complete());assertEquals(HandshakeSession.Result.ACCEPTED,s.accept(1,"1.3.0",nonce,2));assertTrue(s.complete());
    }
    @Test void missingReplyTimesOutWithoutCompletingConfiguration(){
        var s=new HandshakeSession(1,"1.3.0",UUID.randomUUID());s.start(100);
        assertTrue(s.expired(100+HandshakeSession.TIMEOUT_NANOS));assertFalse(s.complete());
    }
}
