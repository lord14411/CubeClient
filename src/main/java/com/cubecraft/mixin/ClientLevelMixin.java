package com.cubecraft.mixin;

import com.cubecraft.CubecraftConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Client-side time lock: replaces the day time the server sends. Targeted by name; no-op if it differs. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel")
public abstract class ClientLevelMixin {
	@ModifyVariable(method = "setTimeFromServer", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private long cubecraft$lockTime(long dayTime) {
		return CubecraftConfig.lockTime >= 0 ? CubecraftConfig.lockTime : dayTime;
	}
}
