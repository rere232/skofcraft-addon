package ch.rere232.skofaddons.supernova;

import ch.rere232.skofaddons.supernova.client.SupernovaClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The supernova clock, in ticks since the missile reached orbit. Negative means it is over. */
public class SupernovaS2CPacket {
    private final int age;

    public SupernovaS2CPacket(int age) {
        this.age = age;
    }

    public static void encode(SupernovaS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.age);
    }

    public static SupernovaS2CPacket decode(FriendlyByteBuf buf) {
        return new SupernovaS2CPacket(buf.readInt());
    }

    public static void handle(SupernovaS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SupernovaClient.sync(packet.age)));
        context.setPacketHandled(true);
    }
}
