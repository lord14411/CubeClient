package com.cubecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Hold-to-zoom and client-side fullbright. */
public final class Extras {
	private static boolean zooming = false;
	private static int savedFov = 70;
	private static boolean appliedNightVision = false;

	public static void tick(Minecraft mc) {
		boolean wantZoom = mc.screen == null && CubecraftClient.ZOOM != null && CubecraftClient.ZOOM.isDown();
		if (wantZoom && !zooming) {
			savedFov = mc.options.fov().get();
			zooming = true;
			mc.options.fov().set(30);
		} else if (!wantZoom && zooming) {
			mc.options.fov().set(savedFov);
			zooming = false;
		}

		if (mc.level != null && CubecraftConfig.weather > 0) {
			int w = CubecraftConfig.weather;
			mc.level.setRainLevel(w == 1 ? 0f : 1f);
			mc.level.setThunderLevel(w == 3 ? 1f : 0f);
		}

		if (mc.player == null) {
			appliedNightVision = false;
			return;
		}
		if (CubecraftConfig.fullbright) {
			MobEffectInstance cur = mc.player.getEffect(MobEffects.NIGHT_VISION);
			if (cur == null || cur.getDuration() < 600) {
				mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, false, false, false));
			}
			appliedNightVision = true;
		} else if (appliedNightVision) {
			mc.player.removeEffect(MobEffects.NIGHT_VISION);
			appliedNightVision = false;
		}
	}

	private Extras() {
	}
}
