package com.notunanancyowen.minecart.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.notunanancyowen.minecart.dataholders.ImprovedMinecart;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecartRenderer.class)
public abstract class MinecartRendererMixin<T extends AbstractMinecart & ImprovedMinecart> extends EntityRenderer<T> {
    @Shadow
    @Final
    protected EntityModel<T> model;

    @Shadow
    @Final
    private BlockRenderDispatcher blockRenderer;

    protected MinecartRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/vehicle/AbstractMinecart;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void minecartBackport$render(T minecart, float entityYaw, float tickDelta, PoseStack poseStack,
                                         MultiBufferSource buffers, int packedLight, CallbackInfo callback) {
        super.render(minecart, entityYaw, tickDelta, poseStack, buffers, packedLight);
        poseStack.pushPose();

        long randomOffset = minecart.getId() * 493286711L;
        randomOffset = randomOffset * randomOffset * 4392167121L + randomOffset * 98761L;
        float offsetX = (((float)(randomOffset >> 16 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
        float offsetY = (((float)(randomOffset >> 20 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
        float offsetZ = (((float)(randomOffset >> 24 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
        poseStack.translate(offsetX, offsetY, offsetZ);

        boolean interpolating = minecart.getController().hasCurrentLerpSteps();
        float renderYaw = interpolating
                ? minecart.getController().getLerpedYaw(tickDelta)
                : Mth.lerp(tickDelta, minecart.yRotO, minecart.getYRot());
        float renderPitch = interpolating
                ? minecart.getController().getLerpedPitch(tickDelta)
                : Mth.lerp(tickDelta, minecart.xRotO, minecart.getXRot());

        poseStack.translate(0.0F, 0.375F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(renderYaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-renderPitch));

        float hurtTime = minecart.getHurtTime() - tickDelta;
        float damage = Math.max(0.0F, minecart.getDamage() - tickDelta);
        if (hurtTime > 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(hurtTime) * hurtTime * damage / 10.0F * minecart.getHurtDir()));
        }

        int displayOffset = minecart.getDisplayOffset();
        BlockState displayState = minecart.getDisplayBlockState();
        if (displayState.getRenderShape() != RenderShape.INVISIBLE) {
            poseStack.pushPose();
            poseStack.scale(0.75F, 0.75F, 0.75F);
            poseStack.translate(-0.5F, (displayOffset - 8) / 16.0F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            this.blockRenderer.renderSingleBlock(
                    displayState, poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null
            );
            poseStack.popPose();
        }

        poseStack.scale(-1.0F, -1.0F, 1.0F);
        this.model.setupAnim(minecart, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        VertexConsumer vertexConsumer = buffers.getBuffer(this.model.renderType(this.getTextureLocation(minecart)));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
        callback.cancel();
    }

    @Override
    public Vec3 getRenderOffset(T minecart, float tickDelta) {
        if (!minecart.getController().hasCurrentLerpSteps()) {
            return super.getRenderOffset(minecart, tickDelta);
        }

        Vec3 entityPosition = new Vec3(
                Mth.lerp(tickDelta, minecart.xOld, minecart.getX()),
                Mth.lerp(tickDelta, minecart.yOld, minecart.getY()),
                Mth.lerp(tickDelta, minecart.zOld, minecart.getZ())
        );
        return super.getRenderOffset(minecart, tickDelta).add(minecart.getController().getLerpedPosition(tickDelta).subtract(entityPosition));
    }
}
