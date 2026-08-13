package com.notunanancyowen.minecart.client;

import com.notunanancyowen.minecart.MinecartController;
import com.notunanancyowen.minecart.MoveMinecartAlongTrackS2CPacket;
import com.notunanancyowen.minecart.dataholders.ImprovedMinecart;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handle(MoveMinecartAlongTrackS2CPacket packet) {
        if (Minecraft.getInstance().level == null || packet.lerpSteps().isEmpty()) {
            return;
        }

        Entity entity = Minecraft.getInstance().level.getEntity(packet.entityId());
        if (!(entity instanceof ImprovedMinecart minecart) || entity.isControlledByLocalInstance()) {
            return;
        }

        MinecartController<?> controller = minecart.getController();
        controller.enqueueLerpSteps(packet.lerpSteps());
    }
}
