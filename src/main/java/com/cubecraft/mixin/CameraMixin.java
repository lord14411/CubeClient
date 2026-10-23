package com.cubecraft.mixin;

import com.cubecraft.ModelRender;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers where the camera is (also in third person) so models stay put in the world. If a name differs, models fall back to your eye position. */
@Mixin(targets = "net.minecraft.client.Camera")
public abstract class CameraMixin {
	@Inject(method = "setPosition(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
	private void cubecraft$pos(Vec3 pos, CallbackInfo ci) {
		ModelRender.onCameraPos(pos.x(), pos.y(), pos.z());
	}

	@Inject(method = "setRotation(FF)V", at = @At("HEAD"))
	private void cubecraft$rot(float yRot, float xRot, CallbackInfo ci) {
		ModelRender.onCameraRot(yRot, xRot);
	}
}
