package net.expedition.net;

import java.util.List;
import net.expedition.Expedition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: a story scene (title lang key or empty, then the lines). Cinematic: the camera shows {@code anchor}
 * letterboxed; otherwise the lines are heard while the player plays on.
 */
public record CutscenePayload(BlockPos anchor, String title, List<Line> lines, boolean cinematic, Shot shot) implements CustomPacketPayload {
    /**
     * A cinematic shot's camera path: from {@code from} to {@code to} over the scene, looking at the anchor, faded in and
     * out; zero vectors: the client picks a shot near the anchor itself. A sequence number >= 0: the client reports the
     * shot's end (CutsceneDonePayload) so the server can stage the next one.
     */
    public record Shot(org.joml.Vector3f from, org.joml.Vector3f to, int sequence) {
        public static final Shot AUTO = new Shot(new org.joml.Vector3f(), new org.joml.Vector3f(), -1);
        static final StreamCodec<RegistryFriendlyByteBuf, Shot> CODEC = StreamCodec.composite(
            ByteBufCodecs.VECTOR3F, Shot::from, ByteBufCodecs.VECTOR3F, Shot::to, ByteBufCodecs.VAR_INT, Shot::sequence, Shot::new);

        public boolean scripted() {
            return from.lengthSquared() > 0;
        }
    }

    public record Line(String speaker, String text) {
        static final StreamCodec<RegistryFriendlyByteBuf, Line> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Line::speaker, ByteBufCodecs.STRING_UTF8, Line::text, Line::new);
    }

    public static final Type<CutscenePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "cutscene"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CutscenePayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, CutscenePayload::anchor,
        ByteBufCodecs.STRING_UTF8, CutscenePayload::title,
        Line.CODEC.apply(ByteBufCodecs.list()), CutscenePayload::lines,
        ByteBufCodecs.BOOL, CutscenePayload::cinematic,
        Shot.CODEC, CutscenePayload::shot,
        CutscenePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
