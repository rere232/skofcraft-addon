package ch.rere232.skofaddons.jam;

import ch.rere232.skofaddons.SkofAddonsMod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A song for the band. The campfire song is read from {@code data/skofcraft_addons/songs/campfire.json} so a data pack
 * can replace it: each part is a list of {@code [step, note]} pairs, and {@link Instrument#soundFor} says what the notes
 * mean. The instruments play it unless the server has its own file; see {@link MidiSong}.
 */
public final class Song {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SkofAddonsMod.MODID, "campfire");
    private static final int[] NO_NOTES = new int[0];
    private static Song campfire;
    private static Song fromConfig;

    private final int ticksPerStep;
    private final int steps;
    private final Map<Instrument, int[][]> parts;

    Song(int ticksPerStep, int steps, Map<Instrument, int[][]> parts) {
        this.ticksPerStep = ticksPerStep;
        this.steps = steps;
        this.parts = parts;
    }

    /** What the instruments play. May be null if there is no song at all. */
    public static Song current() {
        return fromConfig != null ? fromConfig : campfire;
    }

    /** May be null if the song file is missing or broken. */
    public static Song campfire() {
        return campfire;
    }

    public int ticksPerStep() {
        return ticksPerStep;
    }

    public int steps() {
        return steps;
    }

    /** The instruments this song has a part for. */
    public List<Instrument> activeParts() {
        return List.copyOf(parts.keySet());
    }

    public int[] notes(Instrument instrument, int step) {
        int[][] part = parts.get(instrument);
        return part == null ? NO_NOTES : part[step];
    }

    private static Song parse(JsonObject json) {
        int ticksPerStep = GsonHelper.getAsInt(json, "ticks_per_step");
        int steps = GsonHelper.getAsInt(json, "steps");
        if (ticksPerStep < 1 || steps < 1) {
            throw new IllegalArgumentException("ticks_per_step and steps must be positive");
        }
        JsonObject partsJson = GsonHelper.getAsJsonObject(json, "parts");
        Map<Instrument, int[][]> parts = new EnumMap<>(Instrument.class);
        for (Instrument instrument : Instrument.values()) {
            if (partsJson.has(instrument.part())) {
                parts.put(instrument, parsePart(partsJson.getAsJsonArray(instrument.part()), steps));
            }
        }
        return new Song(ticksPerStep, steps, parts);
    }

    private static int[][] parsePart(JsonArray events, int steps) {
        List<List<Integer>> byStep = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            byStep.add(new ArrayList<>());
        }
        for (JsonElement event : events) {
            JsonArray pair = event.getAsJsonArray();
            int step = pair.get(0).getAsInt();
            int note = pair.get(1).getAsInt();
            if (step < 0 || step >= steps || note < Instrument.LOWEST_NOTE || note > Instrument.HIGHEST_NOTE) {
                throw new IllegalArgumentException("Bad note [" + step + ", " + note + "]");
            }
            byStep.get(step).add(note);
        }
        int[][] part = new int[steps][];
        for (int i = 0; i < steps; i++) {
            part[i] = byStep.get(i).stream().mapToInt(Integer::intValue).toArray();
        }
        return part;
    }

    @Mod.EventBusSubscriber(modid = SkofAddonsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Loader extends SimpleJsonResourceReloadListener {
        private Loader() {
            super(new Gson(), "songs");
        }

        @SubscribeEvent
        public static void onAddReloadListeners(AddReloadListenerEvent event) {
            event.addListener(new Loader());
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            fromConfig = MidiSong.load();
            campfire = null;
            JsonElement file = files.get(ID);
            if (file == null) {
                SkofAddonsMod.LOGGER.error("Song file {} is missing", ID);
                return;
            }
            try {
                campfire = parse(file.getAsJsonObject());
            } catch (RuntimeException e) {
                SkofAddonsMod.LOGGER.error("Couldn't read song file {}", ID, e);
            }
        }
    }
}
