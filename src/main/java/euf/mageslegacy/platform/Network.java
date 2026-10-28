package euf.mageslegacy.platform;

import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Native NeoForge payload transport. Each channel has a bounded wire codec and explicit direction. */
public final class Network {
    private static final int MAX_BYTES = 1_048_576;
    private static final Map<ResourceLocation, ServerReceiver> SERVER = new HashMap<>();
    private static final Map<ResourceLocation, ClientReceiver> CLIENT = new HashMap<>();
    @FunctionalInterface public interface ServerReceiver {
        void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, Object sender);
    }
    @FunctionalInterface public interface ClientReceiver {
        void receive(net.minecraft.client.Minecraft client, net.minecraft.client.multiplayer.ClientPacketListener handler, FriendlyByteBuf buf, Object sender);
    }
    public record Payload(ResourceLocation id, byte[] bytes) implements CustomPacketPayload {
        @Override public Type<Payload> type() { return new Type<>(id); }
    }
    public static FriendlyByteBuf create() { return new FriendlyByteBuf(Unpooled.buffer()); }
    private static Payload payload(ResourceLocation id, FriendlyByteBuf buf) {
        try {
            if (buf.readableBytes() > MAX_BYTES) throw new IllegalArgumentException("Payload too large: " + id);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            return new Payload(id, bytes);
        } finally { buf.release(); }
    }
    public static void register(RegisterPayloadHandlersEvent event, String namespace, String... clientChannels) {
        var registrar = event.registrar("neoforge-1.21.1-v1");
        for (var entry : SERVER.entrySet()) {
            ResourceLocation id = entry.getKey();
            registrar.playToServer(new CustomPacketPayload.Type<Payload>(id), codec(id), (p, context) -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(p.bytes()));
                try { entry.getValue().receive(player.getServer(), player, player.connection, buf, null); }
                finally { buf.release(); }
            });
        }
        for (String name : clientChannels) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, name);
            registrar.playToClient(new CustomPacketPayload.Type<Payload>(id), codec(id), (p, context) -> {
                ClientReceiver receiver = CLIENT.get(id);
                if (receiver == null) throw new IllegalStateException("No client receiver for " + id);
                ClientDispatch.handle(receiver, p);
            });
        }
    }
    private static StreamCodec<RegistryFriendlyByteBuf, Payload> codec(ResourceLocation id) {
        return StreamCodec.of((buf, p) -> buf.writeByteArray(p.bytes()), buf -> new Payload(id, buf.readByteArray(MAX_BYTES)));
    }
    public static final class Server {
        public static void registerGlobalReceiver(ResourceLocation id, ServerReceiver receiver) { SERVER.put(id, receiver); }
        public static void send(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf) {
            if (!canSend(player, id)) { buf.release(); return; }
            PacketDistributor.sendToPlayer(player, payload(id, buf));
        }
        public static boolean canSend(ServerPlayer player, ResourceLocation id) { return player.connection != null && player.connection.hasChannel(id); }
    }
    public static final class Client {
        public static void registerGlobalReceiver(ResourceLocation id, ClientReceiver receiver) { CLIENT.put(id, receiver); }
        public static void send(ResourceLocation id, FriendlyByteBuf buf) { PacketDistributor.sendToServer(payload(id, buf)); }
        public static boolean canSend(ResourceLocation id) { return net.minecraft.client.Minecraft.getInstance().getConnection() != null && net.minecraft.client.Minecraft.getInstance().getConnection().hasChannel(id); }
    }
    /** Only resolved on receipt on the physical client; never initialized on a dedicated server. */
    private static final class ClientDispatch {
        static void handle(ClientReceiver receiver, Payload payload) {
            var client = net.minecraft.client.Minecraft.getInstance();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.bytes()));
            try { receiver.receive(client, client.getConnection(), buf, null); }
            finally { buf.release(); }
        }
    }
}
