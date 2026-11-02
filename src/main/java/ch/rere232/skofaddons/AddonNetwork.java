package ch.rere232.skofaddons;

import ch.rere232.skofaddons.supernova.SupernovaS2CPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AddonNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(SkofAddonsMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private AddonNetwork() {}

    static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SupernovaS2CPacket.class, SupernovaS2CPacket::encode, SupernovaS2CPacket::decode, SupernovaS2CPacket::handle);
    }
}
