package ch.rere232.skofaddons.jam;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The campfire band. Each one is a part in the song file and a note block sound to play it with.
 * <p>
 * Notes are semitones above F#3, from {@link #LOWEST_NOTE} to {@link #HIGHEST_NOTE}. Minecraft can only pitch a sample
 * an octave either way, so notes beyond an instrument's own two octaves are played on a vanilla sample recorded higher
 * or lower, which keeps every note at its true pitch at the cost of a change of timbre at the edges.
 * <p>
 * The drums read their notes as kit pieces instead: see the DRUM constants.
 */
public enum Instrument {
    WHISTLE("whistle", SoundEvents.NOTE_BLOCK_FLUTE, Instrument.FLUTE_CENTRE),
    DRUMS("drums", SoundEvents.NOTE_BLOCK_BASEDRUM, Instrument.HARP_CENTRE),
    BANJO("banjo", SoundEvents.NOTE_BLOCK_BANJO, Instrument.HARP_CENTRE),
    FLUTE("flute", SoundEvents.NOTE_BLOCK_FLUTE, Instrument.FLUTE_CENTRE),
    HARMONICA("harmonica", SoundEvents.NOTE_BLOCK_BIT, Instrument.HARP_CENTRE),
    PIANO("piano", SoundEvents.NOTE_BLOCK_HARP, Instrument.HARP_CENTRE);

    public static final int LOWEST_NOTE = -24;
    public static final int HIGHEST_NOTE = 48;

    public static final int DRUM_KICK = 0;
    public static final int DRUM_SNARE = 1;
    public static final int DRUM_HAT = 2;
    public static final int DRUM_CLAP = 3;
    public static final int DRUM_OPEN_HAT = 4;
    public static final int DRUM_LOW_TOM = 5;
    public static final int DRUM_MID_TOM = 6;
    public static final int DRUM_HIGH_TOM = 7;
    public static final int DRUM_CRASH = 8;
    public static final int DRUM_RIDE = 9;

    /** The note each sample plays at normal speed. */
    private static final int BASS_CENTRE = -12;
    private static final int GUITAR_CENTRE = 0;
    private static final int HARP_CENTRE = 12;
    private static final int FLUTE_CENTRE = 24;
    private static final int BELL_CENTRE = 36;
    private static final int REACH = 12;

    private final String part;
    private final Holder<SoundEvent> sound;
    private final int centre;

    Instrument(String part, Holder<SoundEvent> sound, int centre) {
        this.part = part;
        this.sound = sound;
        this.centre = centre;
    }

    public String part() {
        return part;
    }

    private boolean inOwnRange(int note) {
        return Math.abs(note - centre) <= REACH;
    }

    public Holder<SoundEvent> soundFor(int note) {
        if (this == DRUMS) {
            return switch (note) {
                case DRUM_SNARE, DRUM_CLAP -> SoundEvents.NOTE_BLOCK_SNARE;
                case DRUM_HAT, DRUM_OPEN_HAT, DRUM_CRASH, DRUM_RIDE -> SoundEvents.NOTE_BLOCK_HAT;
                default -> SoundEvents.NOTE_BLOCK_BASEDRUM;
            };
        }
        if (inOwnRange(note)) {
            return sound;
        }
        if (note < GUITAR_CENTRE - REACH) {
            return SoundEvents.NOTE_BLOCK_BASS;
        }
        if (note < HARP_CENTRE - REACH) {
            return SoundEvents.NOTE_BLOCK_GUITAR;
        }
        if (note <= HARP_CENTRE + REACH) {
            return SoundEvents.NOTE_BLOCK_HARP;
        }
        return note <= FLUTE_CENTRE + REACH ? SoundEvents.NOTE_BLOCK_FLUTE : SoundEvents.NOTE_BLOCK_BELL;
    }

    public float pitchFor(int note) {
        if (this == DRUMS) {
            return switch (note) {
                case DRUM_CLAP -> 1.4F;
                case DRUM_OPEN_HAT -> 0.7F;
                case DRUM_LOW_TOM -> 1.3F;
                case DRUM_MID_TOM -> 1.6F;
                case DRUM_HIGH_TOM -> 1.9F;
                case DRUM_CRASH -> 0.5F;
                case DRUM_RIDE -> 1.4F;
                default -> 1.0F;
            };
        }
        return (float) Math.pow(2.0D, (note - centreFor(note)) / 12.0D);
    }

    private int centreFor(int note) {
        if (inOwnRange(note)) {
            return centre;
        }
        if (note < GUITAR_CENTRE - REACH) {
            return BASS_CENTRE;
        }
        if (note < HARP_CENTRE - REACH) {
            return GUITAR_CENTRE;
        }
        if (note <= HARP_CENTRE + REACH) {
            return HARP_CENTRE;
        }
        return note <= FLUTE_CENTRE + REACH ? FLUTE_CENTRE : BELL_CENTRE;
    }
}
