package net.expedition.net;

import java.util.List;
import net.expedition.Expedition;
import net.expedition.world.realm.Realm;
import net.expedition.world.realm.RealmLayout;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server → client on login: the realm layout key (derived from the seed, not the seed) and the realms in use. */
public record RealmLayoutPayload(long key, List<Integer> realms, int originX, int originZ) implements CustomPacketPayload {
    public static final Type<RealmLayoutPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "realm_layout"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RealmLayoutPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, RealmLayoutPayload::key,
        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), RealmLayoutPayload::realms,
        ByteBufCodecs.VAR_INT, RealmLayoutPayload::originX,
        ByteBufCodecs.VAR_INT, RealmLayoutPayload::originZ,
        RealmLayoutPayload::new);

    void apply() {
        RealmLayout.configureKey(key, realms.stream().map(i -> Realm.values()[i]).toList(), originX, originZ);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
