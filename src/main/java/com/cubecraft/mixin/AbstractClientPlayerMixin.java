package com.cubecraft.mixin;

import com.cubecraft.CubecraftConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void cubecraft$overrideSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		if (!CubecraftConfig.customSkin.isEmpty() && (Object) this == Minecraft.getInstance().player) {
			PlayerSkin custom = com.cubecraft.CustomSkins.get(CubecraftConfig.customSkin);
			if (custom != null) {
				cir.setReturnValue(custom);
				return;
			}
		}
		if (CubecraftConfig.skin >= 0 && (Object) this == Minecraft.getInstance().player) {
			cir.setReturnValue(DefaultPlayerSkin.get(new UUID(0L, CubecraftConfig.skin)));
		}
	}
}
