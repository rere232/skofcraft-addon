package ch.rere232.skofaddons.supernova;

import ch.rere232.skofaddons.AddonNetwork;
import ch.rere232.skofaddons.SkofAddonsMod;
import ch.rere232.skofcraft.advancement.SkofcraftAdvancements;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/**
 * What happens when a Giga ICBM is aimed at the sun. The server only keeps the clock and sends it to everyone; the
 * sequence itself is drawn by the client. No block is touched: when the time loop closes, the only thing put back is
 * the time of day, so the sun is where it was when the missile hit.
 */
@Mod.EventBusSubscriber(modid = SkofAddonsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Supernova {
    /** Missile still on its way. */
    public static final int FUSE_END = 100;
    /** The sun swells and reddens. */
    public static final int RED_END = 700;
    /** It falls in on itself. */
    public static final int COLLAPSE_END = 800;
    /** A blue-white point, holding its breath. */
    public static final int BLUE_END = 880;
    /** The explosion crosses the sky and reaches the ground. */
    public static final int WAVE_HIT = 1180;
    /** Everything goes white, then the loop closes. */
    public static final int LOOP_START = 1240;
    public static final int LOOP_END = 1380;
    public static final int END = 1420;

    private static final ResourceLocation ADVANCEMENT = ResourceLocation.fromNamespaceAndPath(SkofAddonsMod.MODID, "supernova");
    private static final double AIM_COSINE = 0.995D;
    private static final int SYNC_INTERVAL = 40;
    private static final int NOT_RUNNING = -1;

    private static int age = NOT_RUNNING;
    private static long dayTimeBefore;

    private Supernova() {}

    /** Unit vector towards the sun, matching where vanilla draws it. */
    public static Vec3 sunDirection(Level level, float partialTick) {
        double angle = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        return new Vec3(-Math.sin(angle), Math.cos(angle), 0.0D);
    }

    public static boolean isAimingAtSun(Player player) {
        Level level = player.level();
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }
        Vec3 sun = sunDirection(level, 1.0F);
        return sun.y > 0.0D && player.getLookAngle().dot(sun) > AIM_COSINE;
    }

    /** Returns false if the sun is already busy exploding. */
    public static boolean ignite(ServerLevel level, UUID owner) {
        if (age != NOT_RUNNING) {
            return false;
        }
        MinecraftServer server = level.getServer();
        age = 0;
        dayTimeBefore = server.overworld().getDayTime();
        broadcast();
        ServerPlayer caller = server.getPlayerList().getPlayer(owner);
        if (caller != null) {
            SkofcraftAdvancements.award(caller, ADVANCEMENT);
        }
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || age == NOT_RUNNING) {
            return;
        }
        age++;
        if (age == LOOP_START) {
            // Done while the screen is covered, so the sun doesn't visibly jump
            event.getServer().overworld().setDayTime(dayTimeBefore);
        }
        if (age >= END) {
            age = NOT_RUNNING;
            broadcast();
        } else if (age % SYNC_INTERVAL == 0) {
            broadcast();
        }
    }

    private static void broadcast() {
        AddonNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new SupernovaS2CPacket(age));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (age != NOT_RUNNING && event.getEntity() instanceof ServerPlayer player) {
            AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SupernovaS2CPacket(age));
        }
    }

    /** Static state, so without this a singleplayer session would carry it into the next world it opens. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        age = NOT_RUNNING;
    }
}
