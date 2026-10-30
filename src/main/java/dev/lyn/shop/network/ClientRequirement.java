package dev.lyn.shop.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Shop-owned client requirement with no economy dependency. No client-only classes load on dedicated servers. */
public final class ClientRequirement {
    public static final int PROTOCOL = 1;
    public record Probe(Type<Probe> type, int protocol, String version, UUID nonce) implements CustomPacketPayload {}
    public record Reply(Type<Reply> type, int protocol, String version, UUID nonce) implements CustomPacketPayload {}
    private final String name;
    private final String version;
    private final CustomPacketPayload.Type<Probe> probeType;
    private final CustomPacketPayload.Type<Reply> replyType;
    private final ConfigurationTask.Type taskType;
    private final StreamCodec<FriendlyByteBuf, Probe> probeCodec;
    private final StreamCodec<FriendlyByteBuf, Reply> replyCodec;
    private final Map<ServerConfigurationPacketListenerImpl, RequiredTask> pending = new ConcurrentHashMap<>();
    private boolean registered;

    public static ClientRequirement forMod(String id, String name) {
        String version = FabricLoader.getInstance().getModContainer(id).orElseThrow().getMetadata().getVersion().getFriendlyString();
        return new ClientRequirement(id, name, version);
    }
    public ClientRequirement(String id, String name, String version) {
        if (version == null || version.isBlank() || version.length() > 32) throw new IllegalArgumentException("Invalid client requirement version");
        this.name = Objects.requireNonNull(name); this.version = version;
        probeType = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(id, "client_check"));
        replyType = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(id, "client_check_reply"));
        taskType = new ConfigurationTask.Type(id + ":client_check");
        probeCodec = StreamCodec.of((buf, payload) -> { buf.writeVarInt(payload.protocol()); buf.writeUtf(payload.version(), 32); buf.writeUUID(payload.nonce()); },
                buf -> new Probe(probeType, buf.readVarInt(), buf.readUtf(32), buf.readUUID()));
        replyCodec = StreamCodec.of((buf, payload) -> { buf.writeVarInt(payload.protocol()); buf.writeUtf(payload.version(), 32); buf.writeUUID(payload.nonce()); },
                buf -> new Reply(replyType, buf.readVarInt(), buf.readUtf(32), buf.readUUID()));
    }
    public CustomPacketPayload.Type<Probe> probeType() { return probeType; }
    public CustomPacketPayload.Type<Reply> replyType() { return replyType; }
    public StreamCodec<FriendlyByteBuf, Probe> probeCodec() { return probeCodec; }
    public StreamCodec<FriendlyByteBuf, Reply> replyCodec() { return replyCodec; }
    public Probe probe(UUID nonce) { return new Probe(probeType, PROTOCOL, version, nonce); }
    public Reply reply(UUID nonce) { return new Reply(replyType, PROTOCOL, version, nonce); }
    public String installMessage() {
        return "This server requires " + name + " " + version + " on your client. Install Fabric Loader, Fabric API and the matching " + name + " JAR in your client's mods folder, then reconnect.";
    }
    public synchronized void registerCommon() {
        if (registered) throw new IllegalStateException(name + " networking was registered twice");
        registered = true;
        PayloadTypeRegistry.clientboundConfiguration().register(probeType, probeCodec);
        PayloadTypeRegistry.serverboundConfiguration().register(replyType, replyCodec);
        ServerConfigurationNetworking.registerGlobalReceiver(replyType, (payload, context) -> {
            var listener = context.packetListener();
            var task = pending.get(listener);
            if (task == null) return; // Ignore a late duplicate; it must never complete another task.
            var result = task.session.accept(payload.protocol(), payload.version(), payload.nonce(), System.nanoTime());
            if (result != HandshakeSession.Result.ACCEPTED && result != HandshakeSession.Result.ALREADY_COMPLETE) {
                pending.remove(listener, task);
                listener.disconnect(Component.literal(name + " client check failed (" + result.name().toLowerCase(Locale.ROOT) + "). " + installMessage()));
            }
        });
        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            if (!ServerConfigurationNetworking.canSend(listener, probeType)) {
                listener.disconnect(Component.literal(installMessage())); return;
            }
            RequiredTask task = new RequiredTask(listener);
            pending.put(listener, task);
            listener.addTask(task);
        });
        ServerConfigurationConnectionEvents.DISCONNECT.register((listener, server) -> pending.remove(listener));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> pending.clear());
    }
    private final class RequiredTask implements ConfigurationTask {
        private final ServerConfigurationPacketListenerImpl listener;
        private final HandshakeSession session = new HandshakeSession(PROTOCOL, version, UUID.randomUUID());
        private RequiredTask(ServerConfigurationPacketListenerImpl listener) { this.listener = listener; }
        @Override public Type type() { return taskType; }
        @Override public void start(Consumer<Packet<?>> sender) {
            session.start(System.nanoTime());
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(probe(session.nonce())));
        }
        @Override public boolean tick() {
            if (session.complete()) { pending.remove(listener, this); return true; }
            if (session.expired(System.nanoTime())) {
                pending.remove(listener, this);
                listener.disconnect(Component.literal(name + " client check timed out. " + installMessage()));
            }
            return false;
        }
    }
}
