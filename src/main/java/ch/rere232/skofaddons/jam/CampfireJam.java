package ch.rere232.skofaddons.jam;

import ch.rere232.skofaddons.SkofAddonsMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plays the band, all from the server. Each dimension has one song clock, started by the first player to pick up an
 * instrument and stopped when the last one puts theirs down. Anyone who starts later just reads the same clock, so
 * they come in on the current beat rather than from the top: there is nothing to sync because there is only one clock.
 */
@Mod.EventBusSubscriber(modid = SkofAddonsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CampfireJam {
    private static final float VOLUME = 2.0F;
    private static final Map<UUID, Instrument> PLAYING = new HashMap<>();
    private static final Map<ResourceKey<Level>, Integer> CLOCKS = new HashMap<>();
    /** Players the campfire song is playing from, and how many ticks in it is. */
    private static final Map<UUID, Integer> RECITALS = new HashMap<>();
    private static final int NOT_RUNNING = -1;
    /** How many ticks into {@code /playwild} the server is. */
    private static int everyoneTick = NOT_RUNNING;

    private CampfireJam() {}

    /** Bare {@code /playsound}: every part of the campfire song, once, from wherever the player goes. Again to stop. */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // Merges into vanilla's /playsound, which keeps its arguments and its operator-only requirement
        event.getDispatcher().register(Commands.literal("playsound")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (RECITALS.remove(player.getUUID()) != null) {
                        context.getSource().sendSuccess(() -> Component.translatable("command.skofcraft_addons.playsound.stop"), false);
                        return 1;
                    }
                    if (Song.campfire() == null) {
                        context.getSource().sendFailure(Component.translatable("command.skofcraft_addons.playsound.no_song"));
                        return 0;
                    }
                    RECITALS.put(player.getUUID(), 0);
                    context.getSource().sendSuccess(() -> Component.translatable("command.skofcraft_addons.playsound.start"), false);
                    return 1;
                }));
        event.getDispatcher().register(Commands.literal("playwild")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> toggleEveryone(context.getSource())));
    }

    /** {@code /playwild}: the band's song, once from the top, with everyone online playing it. Again to stop. */
    private static int toggleEveryone(CommandSourceStack source) {
        if (everyoneTick != NOT_RUNNING) {
            everyoneTick = NOT_RUNNING;
            source.sendSuccess(() -> Component.translatable("command.skofcraft_addons.playwild.stop"), true);
            return 1;
        }
        if (Song.current() == null) {
            source.sendFailure(Component.translatable("command.skofcraft_addons.playwild.no_song"));
            return 0;
        }
        everyoneTick = 0;
        source.sendSuccess(() -> Component.translatable("command.skofcraft_addons.playwild.start"), true);
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            player.displayClientMessage(Component.translatable("message.skofcraft_addons.instrument_start"), true);
        }
        return 1;
    }

    /**
     * Hands out the parts afresh every step, so players can join or leave mid-song. Too few players and some play
     * several parts, so the whole song is always heard; too many and the parts come round again.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || everyoneTick == NOT_RUNNING) {
            return;
        }
        Song song = Song.current();
        if (song == null || everyoneTick >= song.ticksPerStep() * song.steps()) {
            everyoneTick = NOT_RUNNING;
            return;
        }
        if (everyoneTick % song.ticksPerStep() == 0) {
            List<ServerPlayer> players = new ArrayList<>(event.getServer().getPlayerList().getPlayers());
            players.removeIf(player -> !player.isAlive() || player.isSpectator());
            List<Instrument> parts = song.activeParts();
            if (!players.isEmpty() && !parts.isEmpty()) {
                int step = everyoneTick / song.ticksPerStep();
                for (int i = 0; i < Math.max(players.size(), parts.size()); i++) {
                    ServerPlayer player = players.get(i % players.size());
                    Instrument part = parts.get(i % parts.size());
                    play(player.serverLevel(), player, part, song.notes(part, step));
                }
            }
        }
        everyoneTick++;
    }

    private static void tickRecitals(ServerLevel level) {
        Song song = Song.campfire();
        for (ServerPlayer player : level.players()) {
            Integer tick = RECITALS.get(player.getUUID());
            if (tick == null) {
                continue;
            }
            if (song == null || !player.isAlive() || tick >= song.ticksPerStep() * song.steps()) {
                RECITALS.remove(player.getUUID());
                continue;
            }
            if (tick % song.ticksPerStep() == 0) {
                for (Instrument instrument : Instrument.values()) {
                    play(level, player, instrument, song.notes(instrument, tick / song.ticksPerStep()));
                }
            }
            RECITALS.put(player.getUUID(), tick + 1);
        }
    }

    /** Starts the instrument, or stops it if the player was already playing this one. */
    public static void toggle(ServerPlayer player, Instrument instrument) {
        if (PLAYING.get(player.getUUID()) == instrument) {
            PLAYING.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.skofcraft_addons.instrument_stop"), true);
        } else {
            PLAYING.put(player.getUUID(), instrument);
            player.displayClientMessage(Component.translatable("message.skofcraft_addons.instrument_start"), true);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        tickRecitals(level);
        // Instruments wait while everyone is playing, or they'd be off the beat. They pick up again afterwards.
        if (everyoneTick != NOT_RUNNING) {
            return;
        }
        List<ServerPlayer> band = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            Instrument instrument = PLAYING.get(player.getUUID());
            if (instrument == null) {
                continue;
            }
            if (player.isAlive() && !player.isSpectator() && InstrumentItem.isHolding(player, instrument)) {
                band.add(player);
            } else {
                PLAYING.remove(player.getUUID());
            }
        }
        if (band.isEmpty()) {
            CLOCKS.remove(level.dimension());
            return;
        }
        Song song = Song.current();
        if (song == null) {
            return;
        }
        int tick = CLOCKS.getOrDefault(level.dimension(), 0);
        if (tick % song.ticksPerStep() == 0) {
            int step = (tick / song.ticksPerStep()) % song.steps();
            for (ServerPlayer player : band) {
                play(level, player, PLAYING.get(player.getUUID()), song.notes(PLAYING.get(player.getUUID()), step));
            }
        }
        // Wraps on a whole loop, so the clock can run for as long as anyone keeps playing
        CLOCKS.put(level.dimension(), (tick + 1) % (song.ticksPerStep() * song.steps()));
    }

    private static void play(ServerLevel level, ServerPlayer player, Instrument instrument, int[] notes) {
        for (int note : notes) {
            level.playSeededSound(null, player.getX(), player.getEyeY(), player.getZ(), instrument.soundFor(note),
                    SoundSource.RECORDS, VOLUME, instrument.pitchFor(note), level.random.nextLong());
            level.sendParticles(ParticleTypes.NOTE, player.getX(), player.getEyeY() + 0.6D, player.getZ(), 0, Math.floorMod(note, 25) / 24.0D, 0.0D, 0.0D, 1.0D);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYING.remove(event.getEntity().getUUID());
        RECITALS.remove(event.getEntity().getUUID());
    }

    /** All static, so without this a singleplayer session would carry them into the next world it opens. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PLAYING.clear();
        RECITALS.clear();
        everyoneTick = NOT_RUNNING;
        CLOCKS.clear();
    }
}
