package net.expedition.route;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.expedition.Expedition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** The ordered route, loaded from data packs and reloaded with /reload. */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class RouteSteps extends SimpleJsonResourceReloadListener {
    public record Entry(ResourceLocation id, RouteStep step) {
    }

    private static List<Entry> steps = List.of();

    private RouteSteps() {
        super(new Gson(), "expedition_route");
    }

    public static List<Entry> all() {
        return steps;
    }

    /** Step at an index of the route, or null when the route is finished. */
    public static Entry get(int index) {
        return index >= 0 && index < steps.size() ? steps.get(index) : null;
    }

    /** Test hook: replace the route. */
    public static void setForTests(List<Entry> entries) {
        steps = List.copyOf(entries);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<Entry> loaded = new ArrayList<>();
        files.forEach((id, json) -> RouteStep.CODEC.parse(JsonOps.INSTANCE, json)
            .resultOrPartial(error -> Expedition.LOG.error("Route step {}: {}", id, error))
            .ifPresent(step -> loaded.add(new Entry(id, step))));
        loaded.sort(Comparator.comparingInt(entry -> entry.step().order()));
        steps = List.copyOf(loaded);
        Expedition.LOG.info("Expedition route: {} steps", steps.size());
    }

    @SubscribeEvent
    static void register(AddReloadListenerEvent event) {
        event.addListener(new RouteSteps());
    }
}
