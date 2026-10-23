package com.cubecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import java.util.concurrent.ThreadLocalRandom;

/** Client-side particle sky: twinkling stars, shooting stars and an aurora. Nobody else sees it. */
public final class SkyFx {
	private static int age = 0;

	private static int lighten(int c) {
		int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
		r += (255 - r) * 55 / 100;
		g += (255 - g) * 55 / 100;
		b += (255 - b) * 55 / 100;
		return (r << 16) | (g << 8) | b;
	}

	private static int mix(int a, int b, double t) {
		int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}

	public static void tick(Minecraft mc) {
		age++;
		if (mc.level == null || mc.player == null) return;
		if (CubecraftConfig.cosmos > 0) return; // the real skybox (Cosmos) replaces the particle sky
		if (!CubecraftConfig.stars && !CubecraftConfig.shooting && !CubecraftConfig.aurora) return;

		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double ex = mc.player.getX();
		double ey = mc.player.getEyeY();
		double ez = mc.player.getZ();

		if (CubecraftConfig.stars) {
			int n = (int) Math.ceil(CubecraftConfig.starDensity / 100.0 * 14);
			int skyLight = lighten(CubecraftConfig.skyColor);
			int fogLight = lighten(CubecraftConfig.fogColor);
			for (int i = 0; i < n; i++) {
				double yaw = rnd.nextDouble() * Math.PI * 2;
				double elev = Math.asin(rnd.nextDouble(0.1, 1.0));
				double dist = rnd.nextDouble(20, 28);
				int colour;
				switch (rnd.nextInt(5)) {
					case 2 -> colour = skyLight;
					case 3 -> colour = fogLight;
					case 4 -> colour = 0xFFE9A8;
					default -> colour = 0xFFFFFF;
				}
				mc.level.addParticle(new DustParticleOptions(colour, (float) rnd.nextDouble(1.6, 3.2)),
					ex + Math.cos(elev) * Math.cos(yaw) * dist,
					ey + Math.sin(elev) * dist,
					ez + Math.cos(elev) * Math.sin(yaw) * dist,
					0, 0, 0);
			}
		}

		if (CubecraftConfig.shooting && rnd.nextInt(100) == 0) {
			double yaw = rnd.nextDouble() * Math.PI * 2;
			double sx = ex + Math.cos(yaw) * 24;
			double sz = ez + Math.sin(yaw) * 24;
			double sy = ey + rnd.nextDouble(10, 20);
			double dx = -Math.sin(yaw) * 0.5;
			double dz = Math.cos(yaw) * 0.5;
			for (int k = 0; k < 14; k++) {
				mc.level.addParticle(ParticleTypes.END_ROD, sx + dx * k * 0.8, sy - k * 0.5, sz + dz * k * 0.8,
					dx * 0.3, -0.12, dz * 0.3);
			}
		}

		if (CubecraftConfig.aurora) {
			for (int i = 0; i < 4; i++) {
				double x = ex + rnd.nextDouble(-35, 35);
				double wave = Math.sin(x * 0.12 + age * 0.04) * 6;
				double z = ez - 30 + wave;
				double y = ey + 12 + rnd.nextDouble(0, 14);
				double t = 0.5 + 0.5 * Math.sin(x * 0.08 + age * 0.03);
				int colour = mix(0x39FF9A, 0xB05CFF, t);
				mc.level.addParticle(new DustParticleOptions(colour, 4.0f), x, y, z, 0, 0, 0);
			}
		}
	}

	private SkyFx() {
	}
}
