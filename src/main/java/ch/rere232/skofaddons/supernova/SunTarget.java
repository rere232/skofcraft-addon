package ch.rere232.skofaddons.supernova;

import ch.rere232.skofcraft.api.IcbmSpecialTarget;
import ch.rere232.skofcraft.hr.MandatoryHr;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** Lets the Orbital Designator mark the sun as a Giga ICBM target. */
public final class SunTarget implements IcbmSpecialTarget {
    @Override
    public boolean isAimedAt(Player player) {
        return Supernova.isAimingAtSun(player);
    }

    @Override
    public Component markedMessage() {
        return Component.translatable("message.skofcraft_addons.icbm_marked_sun").withStyle(ChatFormatting.GOLD);
    }

    @Override
    public Component padStatus(Component payload) {
        return Component.translatable("message.skofcraft_addons.icbm_target_sun", payload).withStyle(ChatFormatting.GOLD);
    }

    @Override
    public void onMissileArrived(ServerLevel level, UUID owner) {
        boolean ignited = Supernova.ignite(level, owner);
        ServerPlayer caller = level.getServer().getPlayerList().getPlayer(owner);
        if (caller != null) {
            MandatoryHr.recordCrime(caller, "message.skofcraft_addons.crime.sun");
            caller.displayClientMessage(Component.translatable(ignited ? "message.skofcraft_addons.supernova_inbound" : "message.skofcraft_addons.supernova_busy")
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }
}
