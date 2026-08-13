package com.notunanancyowen.minecart.mixin;

import com.notunanancyowen.minecart.MinecartController;
import com.notunanancyowen.minecart.dataholders.ImprovedMinecart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin extends Entity implements ImprovedMinecart {
    @Shadow
    private boolean flipped;

    @Shadow
    private boolean onRails;

    @Unique
    private MinecartController<?> minecartBackport$controller;

    protected AbstractMinecartMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("TAIL"))
    private void minecartBackport$createController(EntityType<?> type, Level level, CallbackInfo callback) {
        this.minecartBackport$controller = MinecartController.create((AbstractMinecart & ImprovedMinecart)(Object)this);
        this.flipped = false;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;DDD)V", at = @At("TAIL"))
    private void minecartBackport$alignInitialPosition(EntityType<?> type, Level level, double x, double y, double z, CallbackInfo callback) {
        this.xOld = x;
        this.yOld = y;
        this.zOld = z;
        BlockPos railPos = this.getRailOrMinecartPos();
        this.minecartBackport$controller.adjustToRail(railPos, level.getBlockState(railPos), true);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void minecartBackport$tick(CallbackInfo callback) {
        if (((AbstractMinecart)(Object)this).getHurtTime() > 0) {
            ((AbstractMinecart)(Object)this).setHurtTime(((AbstractMinecart)(Object)this).getHurtTime() - 1);
        }
        if (((AbstractMinecart)(Object)this).getDamage() > 0.0F) {
            ((AbstractMinecart)(Object)this).setDamage(((AbstractMinecart)(Object)this).getDamage() - 1.0F);
        }

        this.checkBelowWorld();
        this.handleNetherPortal();
        this.minecartBackport$controller.tick();
        this.updateInWaterStateAndDoFluidPushing();
        if (this.isInLava()) {
            this.lavaHurt();
            this.fallDistance *= 0.5F;
        }
        this.firstTick = false;
        callback.cancel();
    }

    @Override
    public Vec3 applySlowdown(Vec3 velocity) {
        Vec3 slowed = velocity.multiply(this.minecartBackport$controller.getSpeedRetention(), 0.0, this.minecartBackport$controller.getSpeedRetention());
        return this.isInWater() ? slowed.scale(0.95F) : slowed;
    }

    @Override
    public Vec3 getOldPosition() {
        return new Vec3(this.xOld, this.yOld, this.zOld);
    }

    @Override
    public MinecartController<?> getController() {
        return this.minecartBackport$controller;
    }

    @Override
    public void setOnRails(boolean onRails) {
        this.onRails = onRails;
    }

    @Override
    public boolean isFlipped() {
        return this.flipped;
    }

    @Override
    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
    }

    @Override
    public void applyMinecartGravity() {
        if (!this.isNoGravity()) {
            double gravity = this.isInWater() ? 0.005 : 0.04;
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -gravity, 0.0));
        }
    }

    @Override
    public double getMaxSpeed(ServerLevel level) {
        return this.minecartBackport$controller.getMaxSpeed(level);
    }

    @Override
    public void moveOffRail(ServerLevel level) {
        double maxSpeed = this.getMaxSpeed(level);
        Vec3 velocity = this.getDeltaMovement();
        this.setDeltaMovement(Mth.clamp(velocity.x, -maxSpeed, maxSpeed), velocity.y, Mth.clamp(velocity.z, -maxSpeed, maxSpeed));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.95));
        }
    }

    @Override
    public double moveAlongTrack(BlockPos pos, RailShape shape, double remainingMovement) {
        return this.minecartBackport$controller.moveAlongTrack(pos, shape, remainingMovement);
    }

    @Override
    public BlockPos getRailOrMinecartPos() {
        int x = Mth.floor(this.getX());
        int y = Mth.floor(this.getY());
        int z = Mth.floor(this.getZ());
        double below = this.getY() - 0.1 - 1.0E-5F;
        if (this.level().getBlockState(BlockPos.containing(x, below, z)).is(BlockTags.RAILS)) {
            y = Mth.floor(below);
        }
        return new BlockPos(x, y, z);
    }

    @Override
    public void moveOnRail(ServerLevel level) {
        this.minecartBackport$controller.moveOnRail(level);
    }

    @Override
    public Vec3 getLaunchDirection(BlockPos railPos) {
        BlockState state = this.level().getBlockState(railPos);
        if (!state.is(Blocks.POWERED_RAIL) || !state.getValue(PoweredRailBlock.POWERED)) {
            return Vec3.ZERO;
        }

        RailShape shape = ((BaseRailBlock)state.getBlock()).getRailDirection(state, this.level(), railPos, (AbstractMinecart)(Object)this);
        if (shape == RailShape.EAST_WEST) {
            if (this.minecartBackport$isRedstoneConductor(railPos.west())) {
                return new Vec3(1.0, 0.0, 0.0);
            }
            if (this.minecartBackport$isRedstoneConductor(railPos.east())) {
                return new Vec3(-1.0, 0.0, 0.0);
            }
        } else if (shape == RailShape.NORTH_SOUTH) {
            if (this.minecartBackport$isRedstoneConductor(railPos.north())) {
                return new Vec3(0.0, 0.0, 1.0);
            }
            if (this.minecartBackport$isRedstoneConductor(railPos.south())) {
                return new Vec3(0.0, 0.0, -1.0);
            }
        }
        return Vec3.ZERO;
    }

    @Unique
    private boolean minecartBackport$isRedstoneConductor(BlockPos pos) {
        return this.level().getBlockState(pos).isRedstoneConductor(this.level(), pos);
    }

    @Override
    public boolean isFirstTick() {
        return this.firstTick;
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        Vec3 intendedPosition = this.position().add(movement);
        super.move(type, movement);
        if (this.minecartBackport$controller.handleCollision()) {
            super.move(type, intendedPosition.subtract(this.position()));
        }
        if (type == MoverType.PISTON) {
            this.onRails = false;
        }
    }

    @Override
    public boolean isRideable() {
        return ((AbstractMinecart)(Object)this).getMinecartType() == AbstractMinecart.Type.RIDEABLE;
    }

    @Override
    public boolean isSelfPropelling() {
        return ((AbstractMinecart)(Object)this).getMinecartType() == AbstractMinecart.Type.FURNACE;
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void minecartBackport$push(Entity other, CallbackInfo callback) {
        callback.cancel();
        if (this.level().isClientSide || other.noPhysics || this.noPhysics || this.hasPassenger(other)) {
            return;
        }

        double xDiff = other.getX() - this.getX();
        double zDiff = other.getZ() - this.getZ();
        double distanceSquared = xDiff * xDiff + zDiff * zDiff;
        if (distanceSquared < 1.0E-4F) {
            return;
        }

        double distance = Math.sqrt(distanceSquared);
        xDiff = xDiff / distance * Math.min(1.0, 1.0 / distance) * 0.05;
        zDiff = zDiff / distance * Math.min(1.0, 1.0 / distance) * 0.05;
        if (other instanceof AbstractMinecart otherMinecart) {
            this.minecartBackport$pushMinecart(otherMinecart, xDiff, zDiff);
        } else {
            this.push(-xDiff, 0.0, -zDiff);
            other.push(xDiff / 4.0, 0.0, zDiff / 4.0);
        }
    }

    @Unique
    private void minecartBackport$pushMinecart(AbstractMinecart other, double xDiff, double zDiff) {
        Vec3 velocity = this.getDeltaMovement();
        Vec3 otherVelocity = other.getDeltaMovement();
        boolean otherPowered = other instanceof ImprovedMinecart improved && improved.isSelfPropelling();
        if (otherPowered && !this.isSelfPropelling()) {
            this.setDeltaMovement(velocity.multiply(0.2, 1.0, 0.2));
            this.push(otherVelocity.x - xDiff, 0.0, otherVelocity.z - zDiff);
            other.setDeltaMovement(otherVelocity.multiply(0.95, 1.0, 0.95));
        } else if (!otherPowered && this.isSelfPropelling()) {
            other.setDeltaMovement(otherVelocity.multiply(0.2, 1.0, 0.2));
            other.push(velocity.x + xDiff, 0.0, velocity.z + zDiff);
            this.setDeltaMovement(velocity.multiply(0.95, 1.0, 0.95));
        } else {
            double averageX = (otherVelocity.x + velocity.x) / 2.0;
            double averageZ = (otherVelocity.z + velocity.z) / 2.0;
            this.setDeltaMovement(velocity.multiply(0.2, 1.0, 0.2));
            this.push(averageX - xDiff, 0.0, averageZ - zDiff);
            other.setDeltaMovement(otherVelocity.multiply(0.2, 1.0, 0.2));
            other.push(averageX + xDiff, 0.0, averageZ + zDiff);
        }
    }

    @Inject(method = "lerpTo", at = @At("HEAD"), cancellable = true)
    private void minecartBackport$lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport, CallbackInfo callback) {
        if (!this.minecartBackport$controller.isCustomSyncActive()) {
            this.setPos(x, y, z);
            this.setYRot(yRot % 360.0F);
            this.setXRot(xRot % 360.0F);
        }
        callback.cancel();
    }

    @Inject(method = "lerpMotion", at = @At("HEAD"), cancellable = true)
    private void minecartBackport$lerpMotion(double x, double y, double z, CallbackInfo callback) {
        if (!this.minecartBackport$controller.isCustomSyncActive()) {
            this.setDeltaMovement(x, y, z);
        }
        callback.cancel();
    }

    @Inject(method = "getMotionDirection", at = @At("HEAD"), cancellable = true)
    private void minecartBackport$getMotionDirection(CallbackInfoReturnable<Direction> callback) {
        callback.setReturnValue(this.minecartBackport$controller.getHorizontalFacing());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void minecartBackport$readAdditionalSaveData(CompoundTag tag, CallbackInfo callback) {
        this.flipped = tag.getBoolean("FlippedRotation");
        this.firstTick = tag.getBoolean("HasTicked");
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void minecartBackport$addAdditionalSaveData(CompoundTag tag, CallbackInfo callback) {
        tag.putBoolean("FlippedRotation", this.flipped);
        tag.putBoolean("HasTicked", this.firstTick);
    }

    @Override
    public void checkInsideBlocksForMinecart() {
        this.checkInsideBlocks();
    }

    @Override
    public void reapplyPositionForMinecart() {
        this.reapplyPosition();
    }

    @Override
    public void activateMinecartAt(BlockPos pos, boolean powered) {
        ((AbstractMinecart)(Object)this).activateMinecart(pos.getX(), pos.getY(), pos.getZ(), powered);
    }

    @Override
    public void markVelocityChanged() {
        this.hasImpulse = true;
    }
}
