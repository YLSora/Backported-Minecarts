package com.notunanancyowen.minecart.dataholders;

import com.notunanancyowen.minecart.MinecartController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

public interface ImprovedMinecart {
    boolean isFirstTick();

    Vec3 getLaunchDirection(BlockPos railPos);

    void setOnRails(boolean onRails);

    boolean isFlipped();

    void setFlipped(boolean flipped);

    void applyMinecartGravity();

    void moveOnRail(ServerLevel level);

    void moveOffRail(ServerLevel level);

    double getMaxSpeed(ServerLevel level);

    double moveAlongTrack(BlockPos pos, RailShape shape, double remainingMovement);

    BlockPos getRailOrMinecartPos();

    boolean isRideable();

    boolean isSelfPropelling();

    Vec3 getOldPosition();

    Vec3 applySlowdown(Vec3 velocity);

    void checkInsideBlocksForMinecart();

    void reapplyPositionForMinecart();

    void activateMinecartAt(BlockPos pos, boolean powered);

    void markVelocityChanged();

    MinecartController<?> getController();
}
