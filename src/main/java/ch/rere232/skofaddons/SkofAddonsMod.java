package ch.rere232.skofaddons;

import ch.rere232.skofaddons.supernova.SunTarget;
import ch.rere232.skofcraft.api.IcbmSpecialTargets;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SkofAddonsMod.MODID)
public class SkofAddonsMod {
    public static final String MODID = "skofcraft_addons";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkofAddonsMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        AddonItems.ITEMS.register(modEventBus);
        AddonItems.TABS.register(modEventBus);
        AddonNetwork.register();
        IcbmSpecialTargets.register(ResourceLocation.fromNamespaceAndPath(MODID, "sun"), new SunTarget());
    }
}
