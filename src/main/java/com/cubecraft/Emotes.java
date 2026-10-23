package com.cubecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

import java.util.concurrent.ThreadLocalRandom;

/** Client-side particle emotes. Only you can see them. */
public final class Emotes {
	private static final int DURATION = 80;
	private static Emote current = null;
	private static int ticks = 0;
	public static Emote last = Emote.HEART_SKY;

	public static void start(Emote e) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		current = e;
		last = e;
		ticks = DURATION;
		mc.player.swing(InteractionHand.MAIN_HAND);
		mc.player.displayClientMessage(Component.literal(e.label), true);
	}

	private static int hsv(float h) {
		h = h - (float) Math.floor(h);
		float x = h * 6f;
		int i = (int) x;
		float f = x - i;
		float r, g, b;
		switch (i % 6) {
			case 0 -> { r = 1; g = f; b = 0; }
			case 1 -> { r = 1 - f; g = 1; b = 0; }
			case 2 -> { r = 0; g = 1; b = f; }
			case 3 -> { r = 0; g = 1 - f; b = 1; }
			case 4 -> { r = f; g = 0; b = 1; }
			default -> { r = 1; g = 0; b = 1 - f; }
		}
		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}

	private static void add(Minecraft mc, ParticleOptions p, double x, double y, double z, double vx, double vy, double vz) {
		mc.level.addParticle(p, x, y, z, vx, vy, vz);
	}

	public static void tick(Minecraft mc) {
		if (current == null) return;
		if (ticks <= 0 || mc.player == null || mc.level == null) {
			current = null;
			return;
		}
		ticks--;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double t = DURATION - ticks;
		double p = t / DURATION;
		double px = mc.player.getX();
		double py = mc.player.getY();
		double pz = mc.player.getZ();
		double head = py + mc.player.getBbHeight() + 0.4;

		switch (current) {
			case HEART_SKY -> {
				DustParticleOptions sky = new DustParticleOptions(0x87CEEB, 1.2f);
				for (int i = 0; i < 2; i++) {
					double a = t * 0.45 + i * Math.PI;
					add(mc, sky, px + Math.cos(a) * 0.9, py + 0.2 + p * 1.8, pz + Math.sin(a) * 0.9, 0, 0, 0);
				}
				if (ticks % 4 == 0) add(mc, ParticleTypes.HEART, px, head, pz, 0, 0.05, 0);
			}
			case RAINBOW -> {
				double height = Math.sin(p * Math.PI) * 1.9;
				for (int i = 0; i < 3; i++) {
					double a = t * 0.5 + i * (Math.PI * 2 / 3);
					int colour = hsv((float) (t * 0.03 + i / 3.0));
					add(mc, new DustParticleOptions(colour, 1.3f),
						px + Math.cos(a) * 1.0, py + 0.1 + height, pz + Math.sin(a) * 1.0, 0, 0, 0);
				}
			}
			case SAKURA -> {
				for (int i = 0; i < 3; i++) {
					add(mc, ParticleTypes.CHERRY_LEAVES,
						px + rnd.nextDouble(-1.5, 1.5), py + 2.4 + rnd.nextDouble(), pz + rnd.nextDouble(-1.5, 1.5),
						0, -0.02, 0);
				}
			}
			case FIREWORKS -> {
				if (ticks % 5 == 0) {
					for (int i = 0; i < 10; i++) {
						add(mc, ParticleTypes.FIREWORK, px, head + 0.5, pz,
							rnd.nextDouble(-0.15, 0.15), rnd.nextDouble(0.05, 0.25), rnd.nextDouble(-0.15, 0.15));
					}
				}
				add(mc, ParticleTypes.END_ROD, px, py + 0.2 + p * 2, pz, 0, 0.02, 0);
			}
			case FLAME -> {
				double r = 0.5 + p * 2.2;
				for (int i = 0; i < 4; i++) {
					double a = rnd.nextDouble(Math.PI * 2);
					add(mc, ParticleTypes.FLAME, px + Math.cos(a) * r, py + 0.15, pz + Math.sin(a) * r, 0, 0.02, 0);
				}
			}
			case SNOW -> {
				for (int i = 0; i < 4; i++) {
					add(mc, ParticleTypes.SNOWFLAKE,
						px + rnd.nextDouble(-1.5, 1.5), py + 2.4 + rnd.nextDouble(), pz + rnd.nextDouble(-1.5, 1.5),
						0, -0.03, 0);
				}
			}
			case MUSIC -> {
				if (ticks % 3 == 0) {
					add(mc, ParticleTypes.NOTE, px + rnd.nextDouble(-0.5, 0.5), head, pz + rnd.nextDouble(-0.5, 0.5),
						rnd.nextDouble(), 0, 0);
				}
			}
			case SPARKLE -> {
				for (int i = 0; i < 2; i++) {
					add(mc, ParticleTypes.HAPPY_VILLAGER,
						px + rnd.nextDouble(-0.8, 0.8), py + rnd.nextDouble(0, 2), pz + rnd.nextDouble(-0.8, 0.8), 0, 0, 0);
				}
				add(mc, ParticleTypes.END_ROD, px + rnd.nextDouble(-0.6, 0.6), py + rnd.nextDouble(0, 2),
					pz + rnd.nextDouble(-0.6, 0.6), 0, 0.01, 0);
			}
		}
		if (ticks <= 0) current = null;
	}

	private Emotes() {
	}
}
