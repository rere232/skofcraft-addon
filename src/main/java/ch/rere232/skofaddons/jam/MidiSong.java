package ch.rere232.skofaddons.jam;

import ch.rere232.skofaddons.SkofAddonsMod;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Lets the band play a MIDI file from the server's config folder. The file is read when data loads, so {@code /reload}
 * picks up a new one, and it is never part of the mod: only the server needs it, since clients just hear the sounds.
 */
final class MidiSong {
    private static final String FILE = "skofcraft_addons/band.mid";
    private static final int GAME_TICKS_PER_SECOND = 20;
    private static final int PERCUSSION_CHANNEL = 9;
    private static final int LOWEST_KEY = 54; // F#3, which is note 0
    private static final int DEFAULT_TEMPO = 500000; // microseconds per quarter note, from the MIDI spec
    private static final int META_TEMPO = 0x51;

    private MidiSong() {}

    /** The song in the config folder, or null if there is none or it can't be read. */
    static Song load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            Song song = read(MidiSystem.getSequence(path.toFile()));
            SkofAddonsMod.LOGGER.info("The band is playing {}", path);
            return song;
        } catch (InvalidMidiDataException | IOException | RuntimeException e) {
            SkofAddonsMod.LOGGER.error("Couldn't read {}, the band is playing its own song instead", path, e);
            return null;
        }
    }

    private record Group(int channel, int program, List<long[]> notes) {}

    private static Song read(Sequence sequence) throws InvalidMidiDataException {
        if (sequence.getDivisionType() != Sequence.PPQ) {
            throw new InvalidMidiDataException("Only beat-based timing is supported");
        }
        TreeMap<Long, Integer> tempos = new TreeMap<>();
        tempos.put(0L, DEFAULT_TEMPO);
        List<Group> groups = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            readTrack(track, tempos, groups);
        }
        int resolution = sequence.getResolution();
        int steps = Math.max(1, (int) Math.ceil(seconds(sequence.getTickLength(), tempos, resolution) * GAME_TICKS_PER_SECOND) + 1);

        Map<Instrument, List<TreeSet<Integer>>> parts = new EnumMap<>(Instrument.class);
        Map<Group, Instrument> assignment = assign(groups);
        for (Map.Entry<Group, Instrument> entry : assignment.entrySet()) {
            Instrument instrument = entry.getValue();
            List<TreeSet<Integer>> part = parts.computeIfAbsent(instrument, i -> newPart(steps));
            for (long[] note : entry.getKey().notes()) {
                int step = Math.min(steps - 1, (int) Math.round(seconds(note[0], tempos, resolution) * GAME_TICKS_PER_SECOND));
                part.get(step).add(instrument == Instrument.DRUMS ? drum((int) note[1]) : fit((int) note[1]));
            }
        }

        Map<Instrument, int[][]> result = new EnumMap<>(Instrument.class);
        parts.forEach((instrument, part) -> result.put(instrument,
                part.stream().map(set -> set.stream().mapToInt(Integer::intValue).toArray()).toArray(int[][]::new)));
        return new Song(1, steps, result);
    }

    private static void readTrack(Track track, TreeMap<Long, Integer> tempos, List<Group> groups) {
        Map<Integer, Integer> programs = new LinkedHashMap<>();
        Map<Integer, List<long[]>> notes = new LinkedHashMap<>();
        for (int i = 0; i < track.size(); i++) {
            MidiEvent event = track.get(i);
            MidiMessage message = event.getMessage();
            if (message instanceof MetaMessage meta && meta.getType() == META_TEMPO && meta.getData().length == 3) {
                byte[] data = meta.getData();
                tempos.put(event.getTick(), (data[0] & 0xFF) << 16 | (data[1] & 0xFF) << 8 | data[2] & 0xFF);
            } else if (message instanceof ShortMessage shortMessage) {
                if (shortMessage.getCommand() == ShortMessage.PROGRAM_CHANGE) {
                    programs.putIfAbsent(shortMessage.getChannel(), shortMessage.getData1());
                } else if (shortMessage.getCommand() == ShortMessage.NOTE_ON && shortMessage.getData2() > 0) {
                    notes.computeIfAbsent(shortMessage.getChannel(), c -> new ArrayList<>())
                            .add(new long[] {event.getTick(), shortMessage.getData1()});
                }
            }
        }
        notes.forEach((channel, list) -> groups.add(new Group(channel, programs.getOrDefault(channel, 0), list)));
    }

    /**
     * Gives each part of the file an instrument: percussion to the drums, then anything that sounds like one of the
     * band's instruments to that instrument, then the rest to whoever is still free. With more parts than instruments,
     * the extras double up on the piano.
     */
    private static Map<Group, Instrument> assign(List<Group> groups) {
        Map<Group, Instrument> assignment = new LinkedHashMap<>();
        Deque<Instrument> free = new ArrayDeque<>(List.of(Instrument.WHISTLE, Instrument.FLUTE, Instrument.HARMONICA, Instrument.BANJO, Instrument.PIANO));
        List<Group> unplaced = new ArrayList<>();
        for (Group group : groups) {
            Instrument wanted = group.channel() == PERCUSSION_CHANNEL ? Instrument.DRUMS : family(group.program());
            if (wanted == Instrument.DRUMS || (wanted != null && free.remove(wanted))) {
                assignment.put(group, wanted);
            } else {
                unplaced.add(group);
            }
        }
        for (Group group : unplaced) {
            assignment.put(group, free.isEmpty() ? Instrument.PIANO : free.poll());
        }
        return assignment;
    }

    /** The General MIDI families the band has a player for. */
    private static Instrument family(int program) {
        if (program <= 7) {
            return Instrument.PIANO;
        }
        if (program == 22 || (program >= 64 && program <= 71)) {
            return Instrument.HARMONICA;
        }
        if ((program >= 24 && program <= 31) || (program >= 104 && program <= 108)) {
            return Instrument.BANJO;
        }
        return program >= 72 && program <= 79 ? Instrument.FLUTE : null;
    }

    private static double seconds(long tick, TreeMap<Long, Integer> tempos, int resolution) {
        double seconds = 0.0D;
        long from = 0L;
        int tempo = DEFAULT_TEMPO;
        for (Map.Entry<Long, Integer> change : tempos.headMap(tick, true).entrySet()) {
            seconds += (change.getKey() - from) * (double) tempo / resolution / 1.0E6D;
            from = change.getKey();
            tempo = change.getValue();
        }
        return seconds + (tick - from) * (double) tempo / resolution / 1.0E6D;
    }

    /** The note for a key, moved by whole octaves only if it's beyond every sample Minecraft has. */
    private static int fit(int key) {
        int note = key - LOWEST_KEY;
        while (note < Instrument.LOWEST_NOTE) {
            note += 12;
        }
        while (note > Instrument.HIGHEST_NOTE) {
            note -= 12;
        }
        return note;
    }

    /** General MIDI percussion, to the kit piece closest to it. */
    private static int drum(int key) {
        return switch (key) {
            case 35, 36 -> Instrument.DRUM_KICK;
            case 38, 40 -> Instrument.DRUM_SNARE;
            case 37, 39 -> Instrument.DRUM_CLAP;
            case 46 -> Instrument.DRUM_OPEN_HAT;
            case 41, 43 -> Instrument.DRUM_LOW_TOM;
            case 45, 47 -> Instrument.DRUM_MID_TOM;
            case 48, 50 -> Instrument.DRUM_HIGH_TOM;
            case 49, 52, 55, 57 -> Instrument.DRUM_CRASH;
            case 51, 53, 59 -> Instrument.DRUM_RIDE;
            default -> Instrument.DRUM_HAT;
        };
    }

    private static List<TreeSet<Integer>> newPart(int steps) {
        List<TreeSet<Integer>> part = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            part.add(new TreeSet<>());
        }
        return part;
    }
}
