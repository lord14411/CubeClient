package com.cubecraft.mixin;

import com.cubecraft.Cosmos;
import com.cubecraft.CubecraftConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sky hooks for 1.21.11, where renderSkyDisc takes a single int colour (the old three-float hook never matched).
 * Everything is targeted by name and handlers take only CallbackInfo, so a changed signature just disables a hook.
 */
@Mixin(targets = "net.minecraft.client.renderer.SkyRenderer")
public abstract class SkyRendererMixin {
	/** Cosmic skies paint the vanilla sky disc black; the plain Sky tint recolours it. */
	@ModifyVariable(method = "renderSkyDisc", at = @At("HEAD"), argsOnly = true)
	private int cubecraft$skyColour(int color) {
		if (CubecraftConfig.cosmos > 0) return 0xFF000000;
		if (CubecraftConfig.skyOn) return 0xFF000000 | CubecraftConfig.skyColor;
		return color;
	}

	/** Draw the cosmic sky right after the disc, before the sun, moon and clouds. */
	@Inject(method = "renderSkyDisc", at = @At("RETURN"))
	private void cubecraft$cosmos(CallbackInfo ci) {
		if (CubecraftConfig.cosmos > 0) Cosmos.render();
	}

	/** The blocky vanilla stars would sit on top of the real star layer. */
	@Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
	private void cubecraft$noVanillaStars(CallbackInfo ci) {
		if (CubecraftConfig.cosmos > 0) ci.cancel();
	}
}
