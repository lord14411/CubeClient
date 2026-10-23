package com.cubecraft.mixin;

import com.cubecraft.Cosmos;
import com.cubecraft.CubecraftConfig;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Targeted by name so a changed signature only disables the effect instead of breaking the build. */
@Mixin(targets = "net.minecraft.client.renderer.fog.FogRenderer")
public abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void cubecraft$fogColour(CallbackInfoReturnable<Vector4f> cir) {
		Vector4f v = cir.getReturnValue();
		if (v == null) return;
		if (CubecraftConfig.fogOn) {
			int c = CubecraftConfig.fogColor;
			v.set(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, v.w);
		} else if (CubecraftConfig.cosmos > 0) {
			// fade distant terrain into the horizon colour of the cosmic sky
			int c = Cosmos.fogColor();
			v.set(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, v.w);
		}
	}
}
