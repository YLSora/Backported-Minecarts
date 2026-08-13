package com.notunanancyowen.minecart;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.notunanancyowen.minecart.dataholders.ImprovedMinecart;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

//@SuppressWarnings("all")
public class MinecartController<T extends AbstractMinecart & ImprovedMinecart> {
    public MinecartController(T minecart) {
        this.minecart = minecart;
    }

    public static <T extends AbstractMinecart & ImprovedMinecart> MinecartController<T> create(T minecart) {
        return new MinecartController<>(minecart);
    }

    public static Pair<Vec3i, Vec3i> getAdjacentRailPositionsByShape(RailShape shape) {
        return ADJACENT_RAIL_POSITIONS_BY_SHAPE.get(shape);
    }
    public T minecart;
    private static final Map<RailShape, Pair<Vec3i, Vec3i>> ADJACENT_RAIL_POSITIONS_BY_SHAPE = Maps.newEnumMap(
            Util.make(
                    () -> {
                        Vec3i vec3i = Direction.WEST.getNormal();
                        Vec3i vec3i2 = Direction.EAST.getNormal();
                        Vec3i vec3i3 = Direction.NORTH.getNormal();
                        Vec3i vec3i4 = Direction.SOUTH.getNormal();
                        Vec3i vec3i5 = vec3i.below();
                        Vec3i vec3i6 = vec3i2.below();
                        Vec3i vec3i7 = vec3i3.below();
                        Vec3i vec3i8 = vec3i4.below();
                        return ImmutableMap.of(
                                RailShape.NORTH_SOUTH,
                                Pair.of(vec3i3, vec3i4),
                                RailShape.EAST_WEST,
                                Pair.of(vec3i, vec3i2),
                                RailShape.ASCENDING_EAST,
                                Pair.of(vec3i5, vec3i2),
                                RailShape.ASCENDING_WEST,
                                Pair.of(vec3i, vec3i6),
                                RailShape.ASCENDING_NORTH,
                                Pair.of(vec3i3, vec3i8),
                                RailShape.ASCENDING_SOUTH,
                                Pair.of(vec3i7, vec3i4),
                                RailShape.SOUTH_EAST,
                                Pair.of(vec3i4, vec3i2),
                                RailShape.SOUTH_WEST,
                                Pair.of(vec3i4, vec3i),
                                RailShape.NORTH_WEST,
                                Pair.of(vec3i3, vec3i),
                                RailShape.NORTH_EAST,
                                Pair.of(vec3i3, vec3i2)
                        );
                    }
            )
    );
    @Nullable
    private MinecartController.InterpolatedStep lastReturnedInterpolatedStep;
    private int lastQueriedTicksToNextRefresh;
    private float lastQueriedTickProgress;
    private int ticksToNextRefresh = 0;
    private boolean customSyncActive;
    public final List<MinecartController.Step> stagingLerpSteps = new LinkedList<>();
    public final List<MinecartController.Step> currentLerpSteps = new LinkedList<>();
    public double totalWeight = 0.0;
    public MinecartController.Step initialStep = MinecartController.Step.ZERO;

    public Level getLevel() {
        return this.minecart.level();
    }

    public Vec3 getVelocity() {
        return this.minecart.getDeltaMovement();
    }

    public void setVelocity(Vec3 velocity) {
        this.minecart.setDeltaMovement(velocity);
    }

    public void setVelocity(double x, double y, double z) {
        this.minecart.setDeltaMovement(x, y, z);
    }

    public Vec3 getPos() {
        return this.minecart.position();
    }

    public void setPos(Vec3 pos) {
        this.minecart.setPos(pos);
    }

    public void setPos(double x, double y, double z) {
        this.minecart.setPos(x, y, z);
    }

    public float getPitch() {
        return this.minecart.getXRot();
    }

    public void setPitch(float pitch) {
        this.minecart.setXRot(pitch);
    }

    public float getYaw() {
        return this.minecart.getYRot();
    }

    public void setYaw(float yaw) {
        this.minecart.setYRot(yaw);
    }

    public void setLerpTargetVelocity(double x, double y, double z) {
        this.setVelocity(x, y, z);
    }

    public void enqueueLerpSteps(List<MinecartController.Step> steps) {
        this.customSyncActive = true;
        this.stagingLerpSteps.addAll(steps);
    }

    public boolean isCustomSyncActive() {
        return this.customSyncActive;
    }

    public Direction getHorizontalFacing() {
        return this.minecart.getDirection();
    }

    public Vec3 limitSpeed(Vec3 velocity) {
        return velocity;
    }

    public void tick() {
        if (getLevel().isClientSide) {
            this.tickClient();
            boolean bl = BaseRailBlock.isRail(this.getLevel().getBlockState(this.minecart.getRailOrMinecartPos()));
            this.minecart.setOnRails(bl);
        } else if (this.getLevel() instanceof ServerLevel serverLevel) {
            BlockPos var5 = this.minecart.getRailOrMinecartPos();
            BlockState blockState = this.getLevel().getBlockState(var5);
            if (this.minecart.isFirstTick()) {
                this.minecart.setOnRails(BaseRailBlock.isRail(blockState));
                this.adjustToRail(var5, blockState, true);
            }
            this.minecart.applyMinecartGravity();
            this.minecart.moveOnRail(serverLevel);
            this.syncLerpSteps();
        }
    }

    private void syncLerpSteps() {
        List<MinecartController.Step> steps;
        if (this.stagingLerpSteps.isEmpty()) {
            if (this.minecart.tickCount % 60 != 0) {
                return;
            }
            steps = List.of(new MinecartController.Step(
                    this.getPos(), this.getVelocity(), this.getYaw(), this.getPitch(), 1.0F
            ));
        } else {
            steps = List.copyOf(this.stagingLerpSteps);
            this.stagingLerpSteps.clear();
        }

        MinecartBackport.NETWORK.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> this.minecart),
                new MoveMinecartAlongTrackS2CPacket(this.minecart.getId(), steps)
        );
    }
    private void tickClient() {
        if (--this.ticksToNextRefresh <= 0) {
            this.setInitialStep();
            this.currentLerpSteps.clear();
            if (!this.stagingLerpSteps.isEmpty()) {
                this.currentLerpSteps.addAll(this.stagingLerpSteps);
                this.stagingLerpSteps.clear();
                this.totalWeight = 0.0;

                for (MinecartController.Step step : this.currentLerpSteps) {
                    this.totalWeight = this.totalWeight + step.weight;
                }

                this.ticksToNextRefresh = this.totalWeight == 0.0 ? 0 : 3;
            }
        }

        if (this.hasCurrentLerpSteps()) {
            this.setPos(this.getLerpedPosition(1.0F));
            this.setVelocity(this.getLerpedVelocity(1.0F));
            this.setPitch(this.getLerpedPitch(1.0F));
            this.setYaw(this.getLerpedYaw(1.0F));
        }
    }

    public void setInitialStep() {
        this.initialStep = new MinecartController.Step(this.getPos(), this.getVelocity(), this.getYaw(), this.getPitch(), 0.0F);
    }

    public boolean hasCurrentLerpSteps() {
        return !this.currentLerpSteps.isEmpty();
    }

    public float getLerpedPitch(float tickProgress) {
        MinecartController.InterpolatedStep interpolatedStep = this.getLerpedStep(tickProgress);
        return Mth.rotLerp(interpolatedStep.partialTicksInStep, interpolatedStep.previousStep.xRot, interpolatedStep.currentStep.xRot);
    }

    public float getLerpedYaw(float tickProgress) {
        MinecartController.InterpolatedStep interpolatedStep = this.getLerpedStep(tickProgress);
        return Mth.rotLerp(interpolatedStep.partialTicksInStep, interpolatedStep.previousStep.yRot, interpolatedStep.currentStep.yRot);
    }

    public Vec3 getLerpedPosition(float tickProgress) {
        MinecartController.InterpolatedStep interpolatedStep = this.getLerpedStep(tickProgress);
        return new Vec3(Mth.lerp(interpolatedStep.partialTicksInStep, interpolatedStep.previousStep.position.x, interpolatedStep.currentStep.position.x),  Mth.lerp(interpolatedStep.partialTicksInStep, interpolatedStep.previousStep.position.y, interpolatedStep.currentStep.position.y),  Mth.lerp(interpolatedStep.partialTicksInStep, interpolatedStep.previousStep.position.z, interpolatedStep.currentStep.position.z));
    }

    public Vec3 getLerpedVelocity(float tickProgress) {
        MinecartController.InterpolatedStep interpolatedStep = this.getLerpedStep(tickProgress);
        return new Vec3(Mth.lerp(interpolatedStep.partialTicksInStep, interpolatedStep.previousStep.movement.x, interpolatedStep.currentStep.movement.x), Mth.lerp(interpolatedStep.partialTicksInStep, interpolatedStep.previousStep.movement.y, interpolatedStep.currentStep.movement.y), Mth.lerp(interpolatedStep.partialTicksInStep, interpolatedStep.previousStep.movement.z, interpolatedStep.currentStep.movement.z));
    }

    private MinecartController.InterpolatedStep getLerpedStep(float tickProgress) {
        if (tickProgress == this.lastQueriedTickProgress
                && this.ticksToNextRefresh == this.lastQueriedTicksToNextRefresh
                && this.lastReturnedInterpolatedStep != null) {
            return this.lastReturnedInterpolatedStep;
        } else {
            float f = (3 - this.ticksToNextRefresh + tickProgress) / 3.0F;
            float g = 0.0F;
            float h = 1.0F;
            boolean bl = false;

            int i;
            for (i = 0; i < this.currentLerpSteps.size(); i++) {
                float j = (this.currentLerpSteps.get(i)).weight;
                if (!(j <= 0.0F)) {
                    g += j;
                    if (g >= this.totalWeight * f) {
                        float k = g - j;
                        h = (float)((f * this.totalWeight - k) / j);
                        bl = true;
                        break;
                    }
                }
            }

            if (!bl) {
                i = this.currentLerpSteps.size() - 1;
            }

            MinecartController.Step step = this.currentLerpSteps.get(i);
            MinecartController.Step step2 = i > 0 ? this.currentLerpSteps.get(i - 1) : this.initialStep;
            this.lastReturnedInterpolatedStep = new MinecartController.InterpolatedStep(h, step, step2);
            this.lastQueriedTicksToNextRefresh = this.ticksToNextRefresh;
            this.lastQueriedTickProgress = tickProgress;
            return this.lastReturnedInterpolatedStep;
        }
    }

    public void adjustToRail(BlockPos pos, BlockState blockState, boolean ignoreWeight) {
        if (BaseRailBlock.isRail(blockState)) {
            RailShape railShape = this.getRailShape(pos, blockState);
            Pair<Vec3i, Vec3i> pair = getAdjacentRailPositionsByShape(railShape);
            Vec3 vec3d = new Vec3(pair.getFirst().getX(), pair.getFirst().getY(), pair.getFirst().getZ()).scale(0.5);
            Vec3 vec3d2 = new Vec3(pair.getSecond().getX(), pair.getSecond().getY(), pair.getSecond().getZ()).scale(0.5);
            Vec3 vec3d3 = vec3d.multiply(1, 0, 1);
            Vec3 vec3d4 = vec3d2.multiply(1, 0, 1);
            if (this.getVelocity().length() > 1.0E-5F && this.getVelocity().dot(vec3d3) < this.getVelocity().dot(vec3d4)
                    || this.ascends(vec3d4, railShape)) {
                Vec3 vec3d5 = vec3d3;
                vec3d3 = vec3d4;
                vec3d4 = vec3d5;
            }

            float f = 180.0F - (float)(Math.atan2(vec3d3.z, vec3d3.x) * 180.0 / Math.PI);
            f += this.minecart.isFlipped() ? 180.0F : 0.0F;
            Vec3 vec3d6 = this.getPos();
            boolean bl = vec3d.x != vec3d2.x && vec3d.z != vec3d2.z;
            Vec3 vec3d10;
            if (bl) {
                Vec3 vec3d7 = vec3d2.subtract(vec3d);
                Vec3 vec3d8 = vec3d6.subtract(Vec3.atBottomCenterOf(pos)).subtract(vec3d);
                Vec3 vec3d9 = vec3d7.scale(vec3d7.dot(vec3d8) / vec3d7.dot(vec3d7));
                vec3d10 = Vec3.atBottomCenterOf(pos).add(vec3d).add(vec3d9);
                f = 180.0F - (float)(Math.atan2(vec3d9.z, vec3d9.x) * 180.0 / Math.PI);
                f += this.minecart.isFlipped() ? 180.0F : 0.0F;
            } else {
                boolean bl2 = vec3d.subtract(vec3d2).x != 0.0;
                boolean bl3 = vec3d.subtract(vec3d2).z != 0.0;
                vec3d10 = new Vec3(bl3 ? Vec3.atCenterOf(pos).x : vec3d6.x, pos.getY(), bl2 ? Vec3.atCenterOf(pos).z : vec3d6.z);
            }

            Vec3 vec3d7 = vec3d10.subtract(vec3d6);
            this.setPos(vec3d6.add(vec3d7));
            float g = 0.0F;
            boolean bl4 = vec3d.y != vec3d2.y;
            if (bl4) {
                Vec3 vec3d11 = Vec3.atBottomCenterOf(pos).add(vec3d4);
                double d = vec3d11.distanceTo(this.getPos());
                this.setPos(this.getPos().add(0.0, d + 0.1, 0.0));
                g = this.minecart.isFlipped() ? 45.0F : -45.0F;
            } else {
                this.setPos(this.getPos().add(0.0, 0.1, 0.0));
            }

            this.setAngles(f, g);
            double e = vec3d6.distanceTo(this.getPos());
            if (e > 0.0) {
                this.stagingLerpSteps
                        .add(new MinecartController.Step(this.getPos(), this.getVelocity(), this.getYaw(), this.getPitch(), ignoreWeight ? 0.0F : (float)e));
            }
        }
    }

    private void setAngles(float yaw, float pitch) {
        double d = Math.abs(yaw - this.getYaw());
        if (d >= 175.0 && d <= 185.0) {
            this.minecart.setFlipped(!this.minecart.isFlipped());
            yaw -= 180.0F;
            pitch *= -1.0F;
        }

        pitch = Mth.clamp(pitch, -45.0F, 45.0F);
        this.setPitch(pitch % 360.0F);
        this.setYaw(yaw % 360.0F);
    }

    private RailShape getRailShape(BlockPos pos, BlockState state) {
        return ((BaseRailBlock)state.getBlock()).getRailDirection(state, this.getLevel(), pos, this.minecart);
    }

    public void moveOnRail(ServerLevel world) {
        for (MinecartController.MoveIteration moveIteration = new MinecartController.MoveIteration();
             moveIteration.shouldContinue() && this.minecart.isAlive();
             moveIteration.initial = false
        ) {
            Vec3 vec3d = this.getVelocity();
            BlockPos blockPos = this.minecart.getRailOrMinecartPos();
            BlockState blockState = this.getLevel().getBlockState(blockPos);
            boolean bl = BaseRailBlock.isRail(blockState);
            if (this.minecart.isOnRails() != bl) {
                this.minecart.setOnRails(bl);
                this.adjustToRail(blockPos, blockState, false);
            }

            if (bl) {
                this.minecart.resetFallDistance();
                this.minecart.reapplyPositionForMinecart();
                if (blockState.is(Blocks.ACTIVATOR_RAIL)) {
                    this.minecart.activateMinecartAt(blockPos, blockState.getValue(PoweredRailBlock.POWERED));
                }

                RailShape railShape = this.getRailShape(blockPos, blockState);
                Vec3 vec3d2 = this.calcNewHorizontalVelocity(world, vec3d.multiply(1, 0, 1), moveIteration, blockPos, blockState, railShape);
                if (moveIteration.initial) {
                    moveIteration.remainingMovement = vec3d2.horizontalDistance();
                } else {
                    moveIteration.remainingMovement = moveIteration.remainingMovement + (vec3d2.horizontalDistance() - vec3d.horizontalDistance());
                }

                this.setVelocity(vec3d2);
                moveIteration.remainingMovement = this.minecart.moveAlongTrack(blockPos, railShape, moveIteration.remainingMovement);
                ((BaseRailBlock)blockState.getBlock()).onMinecartPass(blockState, world, blockPos, this.minecart);
            } else {
                this.minecart.moveOffRail(world);
                moveIteration.remainingMovement = 0.0;
            }

            Vec3 vec3d3 = this.getPos();
            Vec3 vec3d2 = vec3d3.subtract(this.minecart.getOldPosition());
            double d = vec3d2.length();
            if (d > 1.0E-5F) {
                if (!(vec3d2.horizontalDistanceSqr() > 1.0E-5F)) {
                    if (!this.minecart.isOnRails()) {
                        this.setPitch(this.minecart.onGround() ? 0.0F : Mth.rotLerp(0.2F, this.getPitch(), 0.0F));
                    }
                } else {
                    float f = 180.0F - (float)(Math.atan2(vec3d2.z, vec3d2.x) * 180.0 / Math.PI);
                    float g = this.minecart.onGround() && !this.minecart.isOnRails()
                            ? 0.0F
                            : 90.0F - (float)(Math.atan2(vec3d2.horizontalDistance(), vec3d2.y) * 180.0 / Math.PI);
                    f += this.minecart.isFlipped() ? 180.0F : 0.0F;
                    g *= this.minecart.isFlipped() ? -1.0F : 1.0F;
                    this.setAngles(f, g);
                }

                this.stagingLerpSteps
                        .add(new MinecartController.Step(vec3d3, this.getVelocity(), this.getYaw(), this.getPitch(), (float)Math.min(d, this.getMaxSpeed(world))));

            } else if (vec3d.horizontalDistanceSqr() > 0.0) {
                this.stagingLerpSteps.add(new MinecartController.Step(vec3d3, this.getVelocity(), this.getYaw(), this.getPitch(), 1.0F));
            }
            this.minecart.markVelocityChanged();
            if (d > 1.0E-5F || moveIteration.initial) {
                this.minecart.checkInsideBlocksForMinecart();
            }
        }
    }

    private Vec3 calcNewHorizontalVelocity(
            ServerLevel world, Vec3 horizontalVelocity, MinecartController.MoveIteration iteration, BlockPos pos, BlockState railState, RailShape railShape
    ) {
        Vec3 vec3d = horizontalVelocity;
        if (!iteration.slopeVelocityApplied) {
            Vec3 vec3d2 = this.applySlopeVelocity(horizontalVelocity, railShape);
            if (vec3d2.horizontalDistanceSqr() != horizontalVelocity.horizontalDistanceSqr()) {
                iteration.slopeVelocityApplied = true;
                vec3d = vec3d2;
            }
        }

        if (iteration.initial) {
            Vec3 vec3d2 = this.applyInitialVelocity(vec3d);
            if (vec3d2.horizontalDistanceSqr() != vec3d.horizontalDistanceSqr()) {
                iteration.decelerated = true;
                vec3d = vec3d2;
            }
        }

        if (!iteration.decelerated) {
            Vec3 vec3d2 = this.decelerateFromPoweredRail(vec3d, railState);
            if (vec3d2.horizontalDistanceSqr() != vec3d.horizontalDistanceSqr()) {
                iteration.decelerated = true;
                vec3d = vec3d2;
            }
        }

        if (iteration.initial) {
            vec3d = this.minecart.applySlowdown(vec3d);
            if (vec3d.lengthSqr() > 0.0) {
                double d = Math.min(vec3d.length(), this.minecart.getMaxSpeed(world));
                vec3d = vec3d.normalize().scale(d);
            }
        }

        if (!iteration.accelerated) {
            Vec3 vec3d2 = this.accelerateFromPoweredRail(vec3d, pos, railState);
            if (vec3d2.horizontalDistanceSqr() != vec3d.horizontalDistanceSqr()) {
                iteration.accelerated = true;
                vec3d = vec3d2;
            }
        }

        return vec3d;
    }

    private Vec3 applySlopeVelocity(Vec3 horizontalVelocity, RailShape railShape) {
        double d = Math.max(0.0078125, horizontalVelocity.horizontalDistance() * 0.02);
        if (this.minecart.isInWater()) {
            d *= 0.2;
        }
        return switch (railShape) {
            case ASCENDING_EAST -> horizontalVelocity.add(-d, 0.0, 0.0);
            case ASCENDING_WEST -> horizontalVelocity.add(d, 0.0, 0.0);
            case ASCENDING_NORTH -> horizontalVelocity.add(0.0, 0.0, d);
            case ASCENDING_SOUTH -> horizontalVelocity.add(0.0, 0.0, -d);
            default -> horizontalVelocity;
        };
    }

    private Vec3 applyInitialVelocity(Vec3 horizontalVelocity) {
        if (this.minecart.getFirstPassenger() instanceof ServerPlayer serverPlayer) {
            Vec3 vec3d = serverPlayer.getDeltaMovement();
            if (vec3d.lengthSqr() > 0.0) {
                Vec3 vec3d2 = vec3d.normalize();
                double d = horizontalVelocity.horizontalDistanceSqr();
                if (vec3d2.lengthSqr() > 0.0 && d < 0.01) {
                    return horizontalVelocity.add(new Vec3(vec3d2.x, 0.0, vec3d2.z).normalize().scale(0.001));
                }
            }

            return horizontalVelocity;
        } else {
            return horizontalVelocity;
        }
    }

    private Vec3 decelerateFromPoweredRail(Vec3 velocity, BlockState railState) {
        if (railState.is(Blocks.POWERED_RAIL) && !railState.getValue(PoweredRailBlock.POWERED)) {
            return velocity.length() < 0.03 ? Vec3.ZERO : velocity.scale(0.5);
        } else {
            return velocity;
        }
    }

    private Vec3 accelerateFromPoweredRail(Vec3 velocity, BlockPos railPos, BlockState railState) {
        if (railState.is(Blocks.POWERED_RAIL) && railState.getValue(PoweredRailBlock.POWERED)) {
            if (velocity.length() > 0.01) {
                return velocity.normalize().scale(velocity.length() + 0.06);
            } else {
                Vec3 vec3d = this.minecart.getLaunchDirection(railPos);
                return vec3d.lengthSqr() <= 0.0 ? velocity : vec3d.scale(velocity.length() + 0.2);
            }
        } else {
            return velocity;
        }
    }
    public double moveAlongTrack(BlockPos blockPos, RailShape railShape, double remainingMovement) {
        if (remainingMovement < 1.0E-5F) {
            return 0.0;
        } else {
            Vec3 vec3d = this.getPos();
            Pair<Vec3i, Vec3i> pair = getAdjacentRailPositionsByShape(railShape);
            Vec3i vec3i = pair.getFirst();
            Vec3i vec3i2 = pair.getSecond();
            Vec3 vec3d2 = this.getVelocity().multiply(1, 0, 1);
            if (vec3d2.length() < 1.0E-5F) {
                this.setVelocity(Vec3.ZERO);
                return 0.0;
            } else {
                boolean bl = vec3i.getY() != vec3i2.getY();
                Vec3 vec3d3 = new Vec3(vec3i2.getX(), vec3i2.getY(), vec3i2.getZ()).scale(0.5).multiply(1, 0, 1);
                Vec3 vec3d4 = new Vec3(vec3i.getX(), vec3i.getY(), vec3i.getZ()).scale(0.5).multiply(1, 0, 1);
                if (vec3d2.dot(vec3d4) < vec3d2.dot(vec3d3)) {
                    vec3d4 = vec3d3;
                }

                Vec3 vec3d5 = Vec3.atBottomCenterOf(blockPos).add(vec3d4).add(0.0, 0.1, 0.0).add(vec3d4.normalize().scale(1.0E-5F));
                if (bl && !this.ascends(vec3d2, railShape)) {
                    vec3d5 = vec3d5.add(0.0, 1.0, 0.0);
                }

                Vec3 vec3d6 = vec3d5.subtract(this.getPos()).normalize();
                vec3d2 = vec3d6.scale(vec3d2.length() / vec3d6.horizontalDistance());
                Vec3 vec3d7 = vec3d.add(vec3d2.normalize().scale(remainingMovement * (bl ? Math.sqrt(2.0) : 1.0)));
                if (vec3d.distanceToSqr(vec3d5) <= vec3d.distanceToSqr(vec3d7)) {
                    remainingMovement = vec3d5.subtract(vec3d7).horizontalDistance();
                    vec3d7 = vec3d5;
                } else {
                    remainingMovement = 0.0;
                }

                this.minecart.move(MoverType.SELF, vec3d7.subtract(vec3d));
                BlockPos newPos = BlockPos.containing(vec3d7);
                BlockState blockState = this.getLevel().getBlockState(newPos);
                if (bl) {
                    if (BaseRailBlock.isRail(blockState)) {
                        RailShape railShape2 = this.getRailShape(newPos, blockState);
                        if (this.restOnVShapedTrack(railShape, railShape2)) {
                            return 0.0;
                        }
                    }

                    double d = vec3d5.multiply(1, 0, 1).distanceTo(this.getPos().multiply(1, 0, 1));
                    double e = vec3d5.y + (this.ascends(vec3d2, railShape) ? d : -d);
                    if (this.getPos().y < e) {
                        this.setPos(this.getPos().x, e, this.getPos().z);
                    }
                }

                if (this.getPos().distanceTo(vec3d) < 1.0E-5F && vec3d7.distanceTo(vec3d) > 1.0E-5F) {
                    this.setVelocity(Vec3.ZERO);
                    return 0.0;
                } else {
                    this.setVelocity(vec3d2);
                    return remainingMovement;
                }
            }
        }
    }

    /**
     * Prevents otherwise stationary minecart from going back and forth on a V-shaped track.
     */
    private boolean restOnVShapedTrack(RailShape currentRailShape, RailShape newRailShape) {
        if (this.getVelocity().lengthSqr() < 0.005
                && newRailShape.isAscending()
                && this.ascends(this.getVelocity(), currentRailShape)
                && !this.ascends(this.getVelocity(), newRailShape)) {
            this.setVelocity(Vec3.ZERO);
            return true;
        } else {
            return false;
        }
    }
    public double getMaxSpeed(ServerLevel world) {
        int configuredSpeed = Mth.clamp(world.getGameRules().getInt(MinecartBackport.MINECART_MAX_SPEED), 1, 1000);
        return configuredSpeed * (this.minecart.isInWater() ? 0.5 : 1.0) / 20.0;
    }
    private boolean ascends(Vec3 velocity, RailShape railShape) {
        return switch (railShape) {
            case ASCENDING_EAST -> velocity.x < 0.0;
            case ASCENDING_WEST -> velocity.x > 0.0;
            case ASCENDING_NORTH -> velocity.z > 0.0;
            case ASCENDING_SOUTH -> velocity.z < 0.0;
            default -> false;
        };
    }

    public double getSpeedRetention() {
        return this.minecart.isVehicle() ? 0.997 : 0.975;
    }

    public boolean handleCollision() {
        boolean bl = this.pickUpEntities(this.minecart.getBoundingBox().inflate(0.2, 0.0, 0.2));
        if (!this.minecart.horizontalCollision && !this.minecart.verticalCollision) {
            return false;
        } else {
            boolean bl2 = this.pushEntities(this.minecart.getBoundingBox().inflate(1.0E-7));
            return bl && !bl2;
        }
    }

    public boolean pickUpEntities(AABB box) {
        if (this.minecart.isRideable() && !this.minecart.isVehicle()) {
            List<Entity> list = this.getLevel().getEntities(this.minecart, box, EntitySelector.pushableBy(this.minecart));
            if (!list.isEmpty()) {
                for (Entity entity : list) {
                    if (!(entity instanceof Player)
                            && !(entity instanceof IronGolem)
                            && !(entity instanceof AbstractMinecart)
                            && !this.minecart.isVehicle()
                            && !entity.isPassenger()) {
                        boolean bl = entity.startRiding(this.minecart);
                        if (bl) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public boolean pushEntities(AABB box) {
        boolean bl = false;
        if (this.minecart.isRideable()) {
            List<Entity> list = this.getLevel().getEntities(this.minecart, box, EntitySelector.pushableBy(this.minecart));
            if (!list.isEmpty()) {
                for (Entity entity : list) {
                    if (entity instanceof Player
                            || entity instanceof IronGolem
                            || entity instanceof AbstractMinecart
                            || this.minecart.isVehicle()
                            || entity.isPassenger()) {
                        entity.push(this.minecart);
                        bl = true;
                    }
                }
            }
        } else {
            for (Entity entity2 : this.getLevel().getEntities(this.minecart, box)) {
                if (!this.minecart.hasPassenger(entity2) && entity2.isPushable() && entity2 instanceof AbstractMinecart) {
                    entity2.push(this.minecart);
                    bl = true;
                }
            }
        }

        return bl;
    }

    record InterpolatedStep(float partialTicksInStep, MinecartController.Step currentStep, MinecartController.Step previousStep) {
    }

    static class MoveIteration {
        double remainingMovement = 0.0;
        boolean initial = true;
        boolean slopeVelocityApplied = false;
        boolean decelerated = false;
        boolean accelerated = false;

        public boolean shouldContinue() {
            return this.initial || this.remainingMovement > 1.0E-5F;
        }
    }

    public record Step(Vec3 position, Vec3 movement, float yRot, float xRot, float weight) {
        public static final MinecartController.Step ZERO = new MinecartController.Step(Vec3.ZERO, Vec3.ZERO, 0.0F, 0.0F, 0.0F);

        public void encode(FriendlyByteBuf buffer) {
            writeVec3(buffer, this.position);
            writeVec3(buffer, this.movement);
            buffer.writeByte(Mth.floor(this.yRot * 256.0F / 360.0F));
            buffer.writeByte(Mth.floor(this.xRot * 256.0F / 360.0F));
            buffer.writeFloat(this.weight);
        }

        public static MinecartController.Step decode(FriendlyByteBuf buffer) {
            Vec3 position = readVec3(buffer);
            Vec3 movement = readVec3(buffer);
            float yRot = buffer.readByte() * 360.0F / 256.0F;
            float xRot = buffer.readByte() * 360.0F / 256.0F;
            return new MinecartController.Step(position, movement, yRot, xRot, buffer.readFloat());
        }

        private static Vec3 readVec3(FriendlyByteBuf buffer) {
            return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }

        private static void writeVec3(FriendlyByteBuf buffer, Vec3 value) {
            buffer.writeDouble(value.x);
            buffer.writeDouble(value.y);
            buffer.writeDouble(value.z);
        }
    }
}
