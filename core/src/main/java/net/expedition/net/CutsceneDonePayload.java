package net.expedition.net;

import net.expedition.Expedition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: a scripted shot has ended (or the player skipped the whole scene). */
public record CutsceneDonePayload(int sequence, boolean skipped) implements CustomPacketPayload {
    public static final Type<CutsceneDonePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "cutscene_done"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CutsceneDonePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, CutsceneDonePayload::sequence, ByteBufCodecs.BOOL, CutsceneDonePayload::skipped, CutsceneDonePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
