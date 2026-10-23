package com.cubecraft.mixin;

import com.cubecraft.ModelRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Three places in the level renderer where 3D models can be drawn. All are targeted by name and take only a CallbackInfo,
 * so a method that does not exist in your game version is simply skipped. ModelRender picks which one draws.
 */
@Mixin(targets = "net.minecraft.client.renderer.LevelRenderer")
public abstract class LevelRendererMixin {
	/** A: right after the entities are submitted (1.21.9+ naming). */
	@Inject(method = "submitEntities", at = @At("RETURN"))
	private void cubecraft$hookA(CallbackInfo ci) {
		ModelRender.hook(0);
	}

	/** B: right after the entities are rendered (older naming). */
	@Inject(method = "renderEntities", at = @At("RETURN"))
	private void cubecraft$hookB(CallbackInfo ci) {
		ModelRender.hook(1);
	}

	/** C: the very end of the level, drawn with the camera rotation applied by us. */
	@Inject(method = "renderLevel", at = @At("RETURN"))
	private void cubecraft$hookC(CallbackInfo ci) {
		ModelRender.hook(2);
	}
}
