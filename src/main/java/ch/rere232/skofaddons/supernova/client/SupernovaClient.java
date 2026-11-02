package ch.rere232.skofaddons.supernova.client;

import ch.rere232.skofaddons.SkofAddonsMod;
import ch.rere232.skofaddons.supernova.Supernova;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Draws the supernova from the server's clock: the sun swells red, collapses to a blue point, and bursts; the wave
 * crosses the sky and hits the ground; then the screen goes black, blue lightning rushes past an emblem, and the
 * world comes back. The lightning and the emblem are generated here, there is no artwork behind them.
 */
@Mod.EventBusSubscriber(modid = SkofAddonsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SupernovaClient {
    private static final ResourceLocation DISC = texture("nova_disc");
    private static final ResourceLocation GLOW = texture("nova_glow");
    private static final ResourceLocation RING = texture("nova_ring");

    private static final float SKY_DISTANCE = 100.0F;
    private static final float NORMAL_SUN = 8.0F;
    private static final float GIANT_SUN = 62.0F;
    private static final float POINT_SUN = 1.6F;
    /** The sun and the wave grow in visible steps rather than smoothly. */
    private static final float SUN_STEP = 1.0F;
    private static final float WAVE_STEP = 6.0F;
    private static final int DISC_FRAMES = 4;
    private static final int TICKS_PER_DISC_FRAME = 5;
    /** Size of a lightning pixel on the loop screen, in GUI units. */
    private static final float PIXEL = 2.0F;
    private static final int RESYNC_TOLERANCE = 5;
    private static final int SHAKE_LEAD = 60;

    private static final int DOME_STACKS = 8;
    private static final int DOME_SLICES = 16;
    private static final float[][] STARS = makeStars();

    private static final int BOLTS = 26;
    private static final int BOLT_POINTS = 14;
    private static final float BOLT_CYCLES = 5.0F;

    private static int age = -1;

    private SupernovaClient() {}

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(SkofAddonsMod.MODID, "textures/misc/" + name + ".png");
    }

    private static float[][] makeStars() {
        Random random = new Random(10842L);
        float[][] stars = new float[500][];
        for (int i = 0; i < stars.length; i++) {
            double y = random.nextDouble() * 2.0D - 1.0D;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double flat = Math.sqrt(1.0D - y * y);
            stars[i] = new float[] {(float) (flat * Math.cos(angle)), (float) y, (float) (flat * Math.sin(angle)), 0.15F + random.nextFloat() * 0.25F};
        }
        return stars;
    }

    // ---------------------------------------------------------------- clock

    public static void sync(int serverAge) {
        if (serverAge < 0) {
            age = -1;
        } else if (age < 0 || Math.abs(age - serverAge) > RESYNC_TOLERANCE) {
            age = serverAge;
        }
    }

    /** From the moment the sun starts to change until the world comes back. */
    public static boolean drawsSky() {
        return age >= Supernova.FUSE_END && age < Supernova.LOOP_END;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || age < 0 || mc.isPaused() || mc.level == null) {
            return;
        }
        age++;
        if (age >= Supernova.END) {
            age = -1;
            return;
        }
        playSounds(mc);
        if (age >= Supernova.WAVE_HIT && age < Supernova.LOOP_START) {
            spawnDebris(mc, mc.level);
        }
    }

    private static void playSounds(Minecraft mc) {
        if (age == Supernova.FUSE_END) {
            play(mc, SoundEvents.BEACON_POWER_SELECT, 0.5F, 1.0F);
        } else if (age > Supernova.FUSE_END && age < Supernova.RED_END && age % 80 == 0) {
            play(mc, SoundEvents.WARDEN_HEARTBEAT, 0.5F, 0.7F);
        } else if (age == Supernova.RED_END) {
            play(mc, SoundEvents.BEACON_DEACTIVATE, 0.5F, 1.0F);
        } else if (age == Supernova.COLLAPSE_END) {
            play(mc, SoundEvents.WARDEN_SONIC_CHARGE, 0.6F, 1.0F);
        } else if (age == Supernova.BLUE_END) {
            play(mc, SoundEvents.WITHER_SPAWN, 0.5F, 1.0F);
            play(mc, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5F, 1.0F);
        } else if (age == Supernova.WAVE_HIT) {
            play(mc, SoundEvents.GENERIC_EXPLODE, 0.4F, 1.0F);
            play(mc, SoundEvents.WARDEN_SONIC_BOOM, 0.5F, 1.0F);
        } else if (age == Supernova.LOOP_START) {
            mc.getSoundManager().stop();
            play(mc, SoundEvents.PORTAL_TRIGGER, 1.6F, 0.6F);
        } else if (age == Supernova.LOOP_END) {
            play(mc, SoundEvents.PORTAL_TRAVEL, 1.4F, 0.3F);
        }
    }

    private static void play(Minecraft mc, SoundEvent sound, float pitch, float volume) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** Dust and ash blown past the player, away from the sun. */
    private static void spawnDebris(Minecraft mc, ClientLevel level) {
        Vec3 at = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 wind = Supernova.sunDirection(level, 1.0F).scale(-1.6D);
        for (int i = 0; i < 80; i++) {
            double x = at.x + (level.random.nextDouble() - 0.5D) * 40.0D;
            double y = at.y + (level.random.nextDouble() - 0.3D) * 20.0D;
            double z = at.z + (level.random.nextDouble() - 0.5D) * 40.0D;
            level.addParticle(i % 4 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD, x, y, z, wind.x, wind.y, wind.z);
        }
    }

    // ---------------------------------------------------------------- look of each phase

    private static float phase(float now, int from, int to) {
        return Mth.clamp((now - from) / (to - from), 0.0F, 1.0F);
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[] {Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2])};
    }

    private static final float[] SUN_YELLOW = {1.0F, 0.93F, 0.7F};
    private static final float[] SUN_ORANGE = {1.0F, 0.5F, 0.18F};
    private static final float[] SUN_RED = {0.95F, 0.16F, 0.08F};
    private static final float[] SUN_WHITE = {1.0F, 1.0F, 1.0F};
    private static final float[] SUN_BLUE = {0.5F, 0.68F, 1.0F};
    private static final float[] SKY_RED = {0.6F, 0.16F, 0.08F};
    private static final float[] SKY_BLACK = {0.01F, 0.012F, 0.04F};
    private static final float[] SKY_BLUE = {0.35F, 0.5F, 1.0F};
    private static final float[] SKY_WHITE = {0.75F, 0.85F, 1.0F};

    private static float[] sunColor(float now) {
        if (now < Supernova.RED_END) {
            float t = phase(now, Supernova.FUSE_END, Supernova.RED_END);
            return t < 0.5F ? mix(SUN_YELLOW, SUN_ORANGE, t * 2.0F) : mix(SUN_ORANGE, SUN_RED, t * 2.0F - 1.0F);
        }
        if (now < Supernova.COLLAPSE_END) {
            return mix(SUN_RED, SUN_WHITE, phase(now, Supernova.RED_END, Supernova.COLLAPSE_END));
        }
        return SUN_BLUE;
    }

    private static float sunSize(float now) {
        if (now < Supernova.RED_END) {
            return Mth.lerp(smooth(phase(now, Supernova.FUSE_END, Supernova.RED_END)), NORMAL_SUN, GIANT_SUN);
        }
        if (now < Supernova.COLLAPSE_END) {
            float t = phase(now, Supernova.RED_END, Supernova.COLLAPSE_END);
            return Mth.lerp(t * t * t, GIANT_SUN, POINT_SUN);
        }
        if (now < Supernova.BLUE_END) {
            return POINT_SUN + 0.6F * Mth.sin(now * 1.2F);
        }
        return Mth.lerp(phase(now, Supernova.BLUE_END, Supernova.WAVE_HIT), POINT_SUN, 40.0F);
    }

    private static float[] skyTint(float now) {
        if (now < Supernova.RED_END) {
            return SKY_RED;
        }
        if (now < Supernova.COLLAPSE_END) {
            return mix(SKY_RED, SKY_BLACK, phase(now, Supernova.RED_END, Supernova.COLLAPSE_END));
        }
        if (now < Supernova.BLUE_END) {
            return SKY_BLACK;
        }
        if (now < Supernova.WAVE_HIT) {
            return mix(SKY_BLACK, SKY_BLUE, phase(now, Supernova.BLUE_END, Supernova.WAVE_HIT));
        }
        return SKY_WHITE;
    }

    private static float skyStrength(float now) {
        if (now < Supernova.RED_END) {
            return 0.75F * smooth(phase(now, Supernova.FUSE_END, Supernova.RED_END));
        }
        return Mth.lerp(phase(now, Supernova.RED_END, Supernova.COLLAPSE_END), 0.75F, 1.0F);
    }

    private static float starStrength(float now) {
        if (now < Supernova.RED_END) {
            return 0.0F;
        }
        if (now < Supernova.BLUE_END) {
            return phase(now, Supernova.RED_END, Supernova.COLLAPSE_END);
        }
        return 1.0F - phase(now, Supernova.BLUE_END, Supernova.WAVE_HIT);
    }

    // ---------------------------------------------------------------- sky

    public static void renderSky(ClientLevel level, float partialTick, PoseStack pose, Camera camera) {
        float now = age + partialTick;
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        drawDome(pose.last().pose(), level, camera, partialTick, now);
        drawStars(pose.last().pose(), Math.max(level.getStarBrightness(partialTick), starStrength(now)));

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(partialTick) * 360.0F));
        drawSun(pose, now);
        pose.popPose();

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawDome(Matrix4f matrix, ClientLevel level, Camera camera, float partialTick, float now) {
        Vec3 sky = level.getSkyColor(camera.getPosition(), partialTick);
        float[] zenith = {(float) sky.x, (float) sky.y, (float) sky.z};
        float[] horizon = mix(zenith, SUN_WHITE, 0.35F);
        float strength = skyStrength(now);
        float[] tint = skyTint(now);
        zenith = mix(zenith, tint, strength);
        horizon = mix(horizon, tint, strength);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int stack = 0; stack < DOME_STACKS; stack++) {
            float low = -Mth.HALF_PI + Mth.PI * stack / DOME_STACKS;
            float high = -Mth.HALF_PI + Mth.PI * (stack + 1) / DOME_STACKS;
            float[] lowColor = domeColor(low, zenith, horizon);
            float[] highColor = domeColor(high, zenith, horizon);
            for (int slice = 0; slice < DOME_SLICES; slice++) {
                float a = Mth.TWO_PI * slice / DOME_SLICES;
                float b = Mth.TWO_PI * (slice + 1) / DOME_SLICES;
                domeVertex(buffer, matrix, low, a, lowColor);
                domeVertex(buffer, matrix, low, b, lowColor);
                domeVertex(buffer, matrix, high, b, highColor);
                domeVertex(buffer, matrix, high, a, highColor);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static float[] domeColor(float elevation, float[] zenith, float[] horizon) {
        if (elevation >= 0.0F) {
            return mix(horizon, zenith, elevation / Mth.HALF_PI);
        }
        return mix(horizon, new float[] {horizon[0] * 0.4F, horizon[1] * 0.4F, horizon[2] * 0.4F}, -elevation / Mth.HALF_PI);
    }

    private static void domeVertex(BufferBuilder buffer, Matrix4f matrix, float elevation, float angle, float[] color) {
        float flat = Mth.cos(elevation) * SKY_DISTANCE;
        buffer.vertex(matrix, flat * Mth.cos(angle), Mth.sin(elevation) * SKY_DISTANCE, flat * Mth.sin(angle))
                .color(color[0], color[1], color[2], 1.0F).endVertex();
    }

    private static void drawStars(Matrix4f matrix, float brightness) {
        if (brightness <= 0.01F) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] star : STARS) {
            float x = star[0] * SKY_DISTANCE;
            float y = star[1] * SKY_DISTANCE;
            float z = star[2] * SKY_DISTANCE;
            float s = star[3];
            // Two offsets square to the star's direction, enough for a dot this small
            float ax = -star[2] * s;
            float az = star[0] * s;
            float by = Math.abs(star[1]) < 0.9F ? s : 0.0F;
            float bx = by == 0.0F ? s : 0.0F;
            buffer.vertex(matrix, x - ax - bx, y - by, z - az).color(1.0F, 1.0F, 1.0F, brightness).endVertex();
            buffer.vertex(matrix, x + ax - bx, y - by, z + az).color(1.0F, 1.0F, 1.0F, brightness).endVertex();
            buffer.vertex(matrix, x + ax + bx, y + by, z + az).color(1.0F, 1.0F, 1.0F, brightness).endVertex();
            buffer.vertex(matrix, x - ax + bx, y + by, z - az).color(1.0F, 1.0F, 1.0F, brightness).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static float snap(float value, float step) {
        return Math.max(1, Math.round(value / step)) * step;
    }

    private static void drawSun(PoseStack pose, float now) {
        float size = snap(sunSize(now), SUN_STEP);
        float[] color = sunColor(now);

        if (now >= Supernova.BLUE_END) {
            float t = phase(now, Supernova.BLUE_END, Supernova.WAVE_HIT);
            // The wave comes at you: slow at first while it is far away, then all at once
            sprite(pose, RING, snap(2.0F + 1400.0F * (float) Math.pow(t, 2.2D), WAVE_STEP), SUN_BLUE, 1.0F, true, 0.0F, 1.0F);
            sprite(pose, RING, snap(2.0F + 700.0F * (float) Math.pow(t, 2.6D), WAVE_STEP), SUN_WHITE, 0.7F, true, 0.0F, 1.0F);
        }
        float glowStrength = now >= Supernova.COLLAPSE_END ? 1.0F : 0.55F;
        float glowSize = now >= Supernova.COLLAPSE_END && now < Supernova.BLUE_END ? 30.0F : size * 3.5F;
        sprite(pose, GLOW, glowSize, color, glowStrength, true, 0.0F, 1.0F);

        // The surface boils: the texture holds a few frames side by side
        int frame = ((int) now / TICKS_PER_DISC_FRAME) % DISC_FRAMES;
        sprite(pose, DISC, size, color, 1.0F, false, (float) frame / DISC_FRAMES, (frame + 1.0F) / DISC_FRAMES);
    }

    private static void sprite(PoseStack pose, ResourceLocation texture, float half, float[] color, float alpha, boolean additive,
                               float u0, float u1) {
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        Matrix4f matrix = pose.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(matrix, -half, SKY_DISTANCE, -half).uv(u0, 0.0F).color(color[0], color[1], color[2], alpha).endVertex();
        buffer.vertex(matrix, half, SKY_DISTANCE, -half).uv(u1, 0.0F).color(color[0], color[1], color[2], alpha).endVertex();
        buffer.vertex(matrix, half, SKY_DISTANCE, half).uv(u1, 1.0F).color(color[0], color[1], color[2], alpha).endVertex();
        buffer.vertex(matrix, -half, SKY_DISTANCE, half).uv(u0, 1.0F).color(color[0], color[1], color[2], alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    // ---------------------------------------------------------------- the rest of the world

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!drawsSky()) {
            return;
        }
        float now = age + (float) event.getPartialTick();
        float strength = skyStrength(now);
        float[] tint = skyTint(now);
        event.setRed(Mth.lerp(strength, event.getRed(), tint[0]));
        event.setGreen(Mth.lerp(strength, event.getGreen(), tint[1]));
        event.setBlue(Mth.lerp(strength, event.getBlue(), tint[2]));
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (age < Supernova.WAVE_HIT - SHAKE_LEAD || age >= Supernova.LOOP_START) {
            return;
        }
        float now = age + (float) event.getPartialTick();
        float strength = 2.5F * phase(now, Supernova.WAVE_HIT - SHAKE_LEAD, Supernova.WAVE_HIT);
        event.setPitch(event.getPitch() + Mth.sin(now * 2.7F) * strength);
        event.setYaw(event.getYaw() + Mth.sin(now * 3.3F) * strength);
        event.setRoll(event.getRoll() + Mth.sin(now * 2.1F) * strength * 0.6F);
    }

    /** The sky can change colour but blocks keep their daylight, so the world is tinted from the screen instead. */
    public static void renderOverlay(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (age < Supernova.FUSE_END) {
            return;
        }
        float now = age + partialTick;
        if (now >= Supernova.LOOP_END) {
            fill(g, width, height, 0.0F, 0.0F, 0.0F, 1.0F - phase(now, Supernova.LOOP_END, Supernova.END));
        } else if (now >= Supernova.LOOP_START) {
            fill(g, width, height, 0.0F, 0.0F, 0.0F, 1.0F);
            drawLoop(g, width, height, phase(now, Supernova.LOOP_START, Supernova.LOOP_END));
        } else if (now >= Supernova.WAVE_HIT) {
            float t = phase(now, Supernova.WAVE_HIT, Supernova.LOOP_START);
            fill(g, width, height, 0.85F, 0.92F, 1.0F, Mth.lerp(t, 0.5F, 1.0F));
        } else if (now >= Supernova.BLUE_END) {
            float t = phase(now, Supernova.BLUE_END, Supernova.WAVE_HIT);
            fill(g, width, height, Mth.lerp(t, 0.0F, 0.4F), Mth.lerp(t, 0.0F, 0.6F), Mth.lerp(t, 0.0F, 1.0F), Mth.lerp(t, 0.45F, 0.5F));
        } else if (now >= Supernova.RED_END) {
            float t = phase(now, Supernova.RED_END, Supernova.COLLAPSE_END);
            fill(g, width, height, Mth.lerp(t, 0.6F, 0.0F), Mth.lerp(t, 0.12F, 0.0F), Mth.lerp(t, 0.05F, 0.0F), Mth.lerp(t, 0.18F, 0.45F));
        } else {
            fill(g, width, height, 0.6F, 0.12F, 0.05F, 0.18F * phase(now, Supernova.FUSE_END, Supernova.RED_END));
        }
    }

    private static void fill(GuiGraphics g, int width, int height, float r, float g2, float b, float a) {
        int color = Mth.clamp((int) (a * 255.0F), 0, 255) << 24 | (int) (r * 255.0F) << 16 | (int) (g2 * 255.0F) << 8 | (int) (b * 255.0F);
        g.fill(0, 0, width, height, color);
    }

    // ---------------------------------------------------------------- the loop closing

    private static void drawLoop(GuiGraphics g, int width, int height, float time) {
        g.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = g.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float cx = width / 2.0F;
        float cy = height / 2.0F;
        float reach = Mth.sqrt(cx * cx + cy * cy) * 1.15F;
        float fade = time > 0.9F ? (1.0F - time) * 10.0F : 1.0F;
        Random random = new Random();
        for (int i = 0; i < BOLTS; i++) {
            drawBolt(buffer, matrix, random, i, cx, cy, reach, time, fade);
        }
        float emblem = smooth(Mth.clamp((time - 0.2F) / 0.2F, 0.0F, 1.0F)) * fade;
        float size = Math.min(width, height) * 0.16F;
        if (emblem > 0.0F) {
            drawEmblem(buffer, matrix, cx, cy, size, emblem * (0.75F + 0.25F * Mth.sin(time * 40.0F)));
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        if (emblem > 0.05F) {
            int alpha = Mth.clamp((int) (emblem * 255.0F), 5, 255);
            g.drawCenteredString(Minecraft.getInstance().font, "SKOF", (int) cx, (int) (cy + size * 1.25F), alpha << 24 | 0xAFC4FF);
        }
    }

    /** One crack of lightning, travelling outwards from the middle and speeding up as it nears the edge. */
    private static void drawBolt(BufferBuilder buffer, Matrix4f matrix, Random random, int index, float cx, float cy,
                                 float reach, float time, float fade) {
        float offset = index * 0.381966F;
        float run = time * BOLT_CYCLES + offset;
        int generation = Mth.floor(run);
        float cycle = run - generation;
        // A new path every time it restarts, the same one for as long as it is on its way
        random.setSeed(index * 7919L + generation * 104729L);
        float angle = random.nextFloat() * Mth.TWO_PI;
        float dx = Mth.cos(angle);
        float dy = Mth.sin(angle);
        float head = cycle * cycle * reach;
        float tail = Math.max(reach * 0.03F, head - reach * 0.4F);
        float alpha = Mth.sin(cycle * Mth.PI) * fade;

        float lastX = cx + dx * tail;
        float lastY = cy + dy * tail;
        for (int i = 1; i <= BOLT_POINTS; i++) {
            float radius = Mth.lerp((float) i / BOLT_POINTS, tail, head);
            float jitter = (random.nextFloat() - 0.5F) * radius * 0.12F;
            float x = cx + dx * radius - dy * jitter;
            float y = cy + dy * radius + dx * jitter;
            float width = 0.5F + 2.2F * radius / reach;
            streak(buffer, matrix, lastX, lastY, x, y, width, alpha);
            float branch = random.nextFloat();
            float branchAngle = (random.nextFloat() - 0.5F) * 1.6F;
            if (branch < 0.3F) {
                float cos = Mth.cos(branchAngle);
                float sin = Mth.sin(branchAngle);
                float length = radius * 0.12F;
                streak(buffer, matrix, x, y, x + (dx * cos - dy * sin) * length, y + (dy * cos + dx * sin) * length, width * 0.5F, alpha * 0.7F);
            }
            lastX = x;
            lastY = y;
        }
    }

    private static void streak(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float x2, float y2, float width, float alpha) {
        segment(buffer, matrix, x1, y1, x2, y2, width * 5.0F, 0.2F, 0.3F, 1.0F, alpha * 0.12F);
        segment(buffer, matrix, x1, y1, x2, y2, width * 2.0F, 0.35F, 0.45F, 1.0F, alpha * 0.35F);
        segment(buffer, matrix, x1, y1, x2, y2, width, 0.8F, 0.85F, 1.0F, alpha);
    }

    /** A line drawn as square pixels on a grid, so nothing on this screen is ever at an angle. */
    private static void segment(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float x2, float y2, float width,
                                float r, float g, float b, float a) {
        float cell = snap(width, PIXEL);
        float dx = x2 - x1;
        float dy = y2 - y1;
        int steps = Math.max(1, Mth.ceil(Mth.sqrt(dx * dx + dy * dy) / cell));
        float lastX = Float.NaN;
        float lastY = Float.NaN;
        for (int i = 0; i <= steps; i++) {
            float x = Mth.floor((x1 + dx * i / steps) / cell) * cell;
            float y = Mth.floor((y1 + dy * i / steps) / cell) * cell;
            // Blending adds up, so a cell drawn twice would show as a brighter dot
            if (x != lastX || y != lastY) {
                buffer.vertex(matrix, x, y + cell, 0.0F).color(r, g, b, a).endVertex();
                buffer.vertex(matrix, x + cell, y + cell, 0.0F).color(r, g, b, a).endVertex();
                buffer.vertex(matrix, x + cell, y, 0.0F).color(r, g, b, a).endVertex();
                buffer.vertex(matrix, x, y, 0.0F).color(r, g, b, a).endVertex();
                lastX = x;
                lastY = y;
            }
        }
    }

    /** A snus tin seen from above, with a single pouch glowing inside it. */
    private static void drawEmblem(BufferBuilder buffer, Matrix4f matrix, float cx, float cy, float size, float alpha) {
        ring(buffer, matrix, cx, cy, size, size * 0.1F, alpha);
        ring(buffer, matrix, cx, cy, size * 0.74F, size * 0.03F, alpha * 0.7F);
        for (int i = 0; i < 8; i++) {
            float angle = Mth.TWO_PI * i / 8.0F;
            float x = Mth.cos(angle);
            float y = Mth.sin(angle);
            streak(buffer, matrix, cx + x * size * 0.76F, cy + y * size * 0.76F, cx + x * size * 0.92F, cy + y * size * 0.92F, size * 0.02F, alpha);
        }
        float halfWidth = size * 0.3F;
        segment(buffer, matrix, cx - halfWidth, cy, cx + halfWidth, cy, size * 0.5F, 0.2F, 0.3F, 1.0F, alpha * 0.25F);
        segment(buffer, matrix, cx - halfWidth, cy, cx + halfWidth, cy, size * 0.3F, 0.8F, 0.85F, 1.0F, alpha);
    }

    private static void ring(BufferBuilder buffer, Matrix4f matrix, float cx, float cy, float radius, float width, float alpha) {
        int parts = 48;
        for (int i = 0; i < parts; i++) {
            float a = Mth.TWO_PI * i / parts;
            float b = Mth.TWO_PI * (i + 1) / parts;
            streak(buffer, matrix, cx + Mth.cos(a) * radius, cy + Mth.sin(a) * radius, cx + Mth.cos(b) * radius, cy + Mth.sin(b) * radius, width, alpha);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        age = -1;
    }
}
