package ch.rere232.skofaddons.jam;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Right-click to join the band, again to stop. It plays for as long as it stays in your hand. */
public class InstrumentItem extends Item {
    private final Instrument instrument;

    public InstrumentItem(Instrument instrument, Properties properties) {
        super(properties);
        this.instrument = instrument;
    }

    public static boolean isHolding(Player player, Instrument instrument) {
        return plays(player.getMainHandItem(), instrument) || plays(player.getOffhandItem(), instrument);
    }

    private static boolean plays(ItemStack stack, Instrument instrument) {
        return stack.getItem() instanceof InstrumentItem item && item.instrument == instrument;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            CampfireJam.toggle(serverPlayer, instrument);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skofcraft_addons.instrument").withStyle(ChatFormatting.GRAY));
    }
}
