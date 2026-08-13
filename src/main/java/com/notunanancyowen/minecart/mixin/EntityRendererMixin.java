package com.notunanancyowen.minecart.mixin;

import com.notunanancyowen.minecart.dataholders.ImprovedMinecart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "getRenderOffset", at = @At("RETURN"), cancellable = true)
    private void minecartBackport$offsetPassenger(Entity entity, float tickDelta, CallbackInfoReturnable<Vec3> callback) {
        if (!(entity.getVehicle() instanceof ImprovedMinecart minecart) || !minecart.getController().hasCurrentLerpSteps()) {
            return;
        }

        Entity vehicle = entity.getVehicle();
        Vec3 renderedPosition = minecart.getController().getLerpedPosition(tickDelta);
        Vec3 entityPosition = new Vec3(
                Mth.lerp(tickDelta, vehicle.xOld, vehicle.getX()),
                Mth.lerp(tickDelta, vehicle.yOld, vehicle.getY()),
                Mth.lerp(tickDelta, vehicle.zOld, vehicle.getZ())
        );
        callback.setReturnValue(callback.getReturnValue().add(renderedPosition.subtract(entityPosition)));
    }
}
