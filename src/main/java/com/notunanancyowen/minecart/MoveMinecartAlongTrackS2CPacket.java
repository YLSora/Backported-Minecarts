package com.notunanancyowen.minecart;

import com.notunanancyowen.minecart.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record MoveMinecartAlongTrackS2CPacket(int entityId, List<MinecartController.Step> lerpSteps) {
    public static void encode(MoveMinecartAlongTrackS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
        buffer.writeCollection(packet.lerpSteps, (target, step) -> step.encode(target));
    }

    public static MoveMinecartAlongTrackS2CPacket decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        List<MinecartController.Step> steps = buffer.readCollection(ArrayList::new, MinecartController.Step::decode);
        return new MoveMinecartAlongTrackS2CPacket(entityId, steps);
    }

    public static void handle(MoveMinecartAlongTrackS2CPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handle(packet));
    }
}
