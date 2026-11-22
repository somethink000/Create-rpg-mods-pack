package net.expedition.net;

import net.expedition.Expedition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Expedition.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ExpeditionNetwork {
    private ExpeditionNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(CutscenePayload.TYPE, CutscenePayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> net.expedition.client.CutsceneClient.play(payload)));
        registrar.playToServer(CutsceneDonePayload.TYPE, CutsceneDonePayload.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                net.expedition.route.Prologue.onDone(player, payload.sequence(), payload.skipped());
            }
        }));
        registrar.playToClient(RealmLayoutPayload.TYPE, RealmLayoutPayload.CODEC, (payload, context) -> context.enqueueWork(payload::apply));
    }
}
