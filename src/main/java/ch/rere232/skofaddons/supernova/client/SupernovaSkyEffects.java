package ch.rere232.skofaddons.supernova.client;

import ch.rere232.skofaddons.SkofAddonsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** The vanilla overworld sky, except during a supernova: vanilla's sun can't be hidden any other way. */
public class SupernovaSkyEffects extends DimensionSpecialEffects.OverworldEffects {
    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        if (!SupernovaClient.drawsSky()) {
            return false;
        }
        setupFog.run();
        SupernovaClient.renderSky(level, partialTick, poseStack, camera);
        return true;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY,
                                double camZ, Matrix4f projectionMatrix) {
        return SupernovaClient.drawsSky();
    }

    @Mod.EventBusSubscriber(modid = SkofAddonsMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegister(RegisterDimensionSpecialEffectsEvent event) {
            event.register(BuiltinDimensionTypes.OVERWORLD_EFFECTS, new SupernovaSkyEffects());
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("supernova", SupernovaClient::renderOverlay);
        }
    }
}
