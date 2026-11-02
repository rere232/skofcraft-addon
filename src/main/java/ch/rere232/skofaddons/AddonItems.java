package ch.rere232.skofaddons;

import ch.rere232.skofaddons.jam.Instrument;
import ch.rere232.skofaddons.jam.InstrumentItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class AddonItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SkofAddonsMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SkofAddonsMod.MODID);

    private static final Map<Instrument, RegistryObject<Item>> INSTRUMENTS = new EnumMap<>(Instrument.class);

    static {
        for (Instrument instrument : Instrument.values()) {
            INSTRUMENTS.put(instrument, ITEMS.register(instrument.name().toLowerCase(Locale.ROOT),
                    () -> new InstrumentItem(instrument, new Item.Properties().stacksTo(1))));
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.skofcraft_addons"))
            .icon(() -> new ItemStack(INSTRUMENTS.get(Instrument.BANJO).get()))
            .displayItems((parameters, output) -> INSTRUMENTS.values().forEach(item -> output.accept(item.get())))
            .build());

    private AddonItems() {}
}
