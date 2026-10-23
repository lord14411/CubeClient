package com.cubecraft;

import com.cubecraft.models.ModelData;
import com.cubecraft.models.Xform;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The always-works way to see a model: its surface is sprinkled with coloured dust particles every tick.
 * Uses only the particle system (same as the emotes and the sky), so it needs no render hooks.
 */
public final class ModelHologram {
	private static final int BUDGET = 700; // particles per tick, all models together
	private static boolean ran = false;

	public static boolean running() {
		return ran && ModelRender.showing();
	}

	public static void tick(Minecraft mc) {
		ran = false;
		int mode = CubecraftConfig.modelRender;
		boolean want = mode == 4 || (mode == 0 && ModelRender.hologramFallback());
		if (!want || mc.level == null || mc.player == null) return;

		List<Models.Inst> list = Models.collect(mc, 1f);
		int usable = 0;
		for (Models.Inst in : list) if (Models.get(in.file()).state == Models.READY) usable++;
		if (usable == 0) return;

		int each = Math.min(160, BUDGET / usable);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		float[] o = new float[3];
		for (Models.Inst in : list) {
			Models.Entry e = Models.get(in.file());
			if (e.state != Models.READY) continue;
			ModelData d = e.data;
			ModelData.Points pts = d.points();
			if (pts.n == 0) continue;
			ModelData.Norm n = d.norm[in.zup() ? 1 : 0];
			float rad = (float) Math.toRadians(in.yawDeg());
			float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
			for (int k = 0; k < each; k++) {
				int i = rnd.nextInt(pts.n);
				Xform.point(n, in.zup(), in.scale(), cos, sin, pts.xyz[i * 3], pts.xyz[i * 3 + 1], pts.xyz[i * 3 + 2], o);
				mc.level.addParticle(new DustParticleOptions(pts.rgb[i], 0.7f), in.x() + o[0], in.y() + o[1], in.z() + o[2], 0, 0, 0);
			}
		}
		ran = true;
		ModelRender.markShown();
	}

	private ModelHologram() {
	}
}
