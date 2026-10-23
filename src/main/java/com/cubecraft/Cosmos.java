package com.cubecraft;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Cosmic sky: a pre-rendered cube-map nebula, a transparent star layer, aurora curtains and meteors.
 * Drawn from SkyRendererMixin right after the vanilla sky disc (which is painted black). Client-side only.
 */
public final class Cosmos {
	public static final String[] NAMES = {"Off", "Violet", "Gold", "Crimson", "Emerald", "Deep Space"};
	private static final String[] KEY = {null, "violet", "gold", "crimson", "emerald", "deep"};
	/** Horizon colour per sky, used to fade distant terrain into the sky (measured from the textures). */
	private static final int[] FOG = {0x000000, 0x481D51, 0x3E2412, 0x471208, 0x0F3734, 0x181F39};

	private static final Logger LOG = LoggerFactory.getLogger("cubecraft");
	private static final String[] FACE = {"px", "nx", "py", "ny", "pz", "nz"};
	private static final float[][] C = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
	private static final float[][] U = {{0, 1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {0, 1, 0}, {0, 1, 0}};
	private static final float[][] UV = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
	private static final float R = 50f;
	private static final Matrix4f ID = new Matrix4f();

	/** Render-type factories to try in order; if the first one throws at draw time we move to the next. */
	@SuppressWarnings("unchecked")
	private static final Function<Identifier, RenderType>[] FACTORIES = new Function[]{
		(Function<Identifier, RenderType>) RenderTypes::fireScreenEffect,
		(Function<Identifier, RenderType>) RenderTypes::blockScreenEffect
	};
	private static int factory = 0;
	private static final Map<String, RenderType> TYPES = new HashMap<>();

	private static boolean broken = false;
	private static int frames = 0;
	private static final List<Meteor> METEORS = new ArrayList<>();
	private static long nextMeteor = 0;

	private static final class Meteor {
		final long start = System.nanoTime();
		final float[] p0 = new float[3];
		final float[] t0 = new float[3];
	}

	public static String status() {
		if (CubecraftConfig.cosmos <= 0) return "Pick a sky above.";
		if (broken) return "Sky failed - see logs/latest.log";
		if (frames == 0) return "Waiting for the sky hook...";
		return "Sky running";
	}

	public static int fogColor() {
		int i = CubecraftConfig.cosmos;
		return FOG[Math.max(0, Math.min(FOG.length - 1, i))];
	}

	private static RenderType type(String name) {
		RenderType t = TYPES.get(name);
		if (t == null) {
			t = FACTORIES[factory].apply(Identifier.fromNamespaceAndPath("cubecraft", "textures/sky/" + name + ".png"));
			TYPES.put(name, t);
		}
		return t;
	}

	public static void render() {
		if (broken || CubecraftConfig.cosmos <= 0) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		try {
			draw(mc);
			frames++;
		} catch (Throwable t) {
			LOG.error("CubeCraft sky renderer failed with render type #" + factory, t);
			TYPES.clear();
			factory++;
			if (factory >= FACTORIES.length) {
				broken = true;
			}
		}
	}

	private static void draw(Minecraft mc) {
		MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
		double sec = System.nanoTime() / 1.0e9;
		float rain = 1f - 0.7f * mc.level.getRainLevel(1f);
		float bright = Math.max(0f, Math.min(1f, CubecraftConfig.cosmosBright / 100f)) * rain;
		int v = Math.round(255 * bright);
		int argb = 0xFF000000 | (v << 16) | (v << 8) | v;

		Matrix4f m = new Matrix4f();
		if (CubecraftConfig.cosmosSpin) m.rotateY((float) Math.toRadians((sec * 0.35) % 360.0));
		String key = KEY[CubecraftConfig.cosmos];

		for (int f = 0; f < 6; f++) {
			RenderType rt = type(key + "_" + FACE[f]);
			face(bs.getBuffer(rt), m, f, argb);
			bs.endBatch(rt);
		}
		for (int f = 0; f < 6; f++) {
			RenderType rt = type("stars_" + FACE[f]);
			face(bs.getBuffer(rt), m, f, argb);
			bs.endBatch(rt);
		}

		if (CubecraftConfig.aurora || CubecraftConfig.shooting) {
			RenderType rt = type("white");
			VertexConsumer vc = bs.getBuffer(rt);
			if (CubecraftConfig.aurora) aurora(vc, (float) sec, bright);
			if (CubecraftConfig.shooting) meteors(vc, bright);
			bs.endBatch(rt);
		}
	}

	/** One cube face. The corner order is counter-clockwise when seen from inside the cube. */
	static void face(VertexConsumer vc, Matrix4f m, int f, int argb) {
		float[] c = C[f], u = U[f];
		float[] r = {c[1] * u[2] - c[2] * u[1], c[2] * u[0] - c[0] * u[2], c[0] * u[1] - c[1] * u[0]};
		for (float[] uv : UV) {
			float s = 2 * uv[0] - 1, t = 2 * uv[1] - 1;
			float x = (c[0] + s * r[0] - t * u[0]) * R;
			float y = (c[1] + s * r[1] - t * u[1]) * R;
			float z = (c[2] + s * r[2] - t * u[2]) * R;
			vc.addVertex(m, x, y, z).setUv(uv[0], uv[1]).setColor(argb);
		}
	}

	private static int argb(float a, int rgb) {
		int al = Math.max(0, Math.min(255, Math.round(a * 255)));
		return (al << 24) | (rgb & 0xFFFFFF);
	}

	private static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}

	/** A quad drawn with both windings so it shows whether or not the pipeline culls back faces. */
	private static void quad2(VertexConsumer vc, float[] a, int ca, float[] b, int cb, float[] c, int cc, float[] d, int cd) {
		put(vc, a, ca); put(vc, b, cb); put(vc, c, cc); put(vc, d, cd);
		put(vc, d, cd); put(vc, c, cc); put(vc, b, cb); put(vc, a, ca);
	}

	private static void put(VertexConsumer vc, float[] p, int color) {
		vc.addVertex(ID, p[0], p[1], p[2]).setUv(0.5f, 0.5f).setColor(color);
	}

	private static float[] dir(float az, float el, float radius) {
		float ce = (float) Math.cos(el);
		return new float[]{(float) Math.cos(az) * ce * radius, (float) Math.sin(el) * radius, (float) Math.sin(az) * ce * radius};
	}

	/** Soft green-to-purple curtains that wave slowly. */
	private static void aurora(VertexConsumer vc, float t, float bright) {
		final int n = 44;
		for (int layer = 0; layer < 2; layer++) {
			float a0 = 0.6f + layer * 0.5f;
			float span = 2.4f;
			float radius = 46f - layer * 2f;
			float[][] bot = new float[n + 1][], mid = new float[n + 1][], top = new float[n + 1][];
			int[] col = new int[n + 1];
			float[] alpha = new float[n + 1];
			for (int i = 0; i <= n; i++) {
				float k = i / (float) n;
				float az = a0 + span * k;
				float wave = (float) (Math.sin(az * 3 + t * 0.35 + layer * 2) * 0.07 + Math.sin(az * 7 - t * 0.6) * 0.025);
				float base = 0.10f + layer * 0.07f + wave;
				float height = 0.30f + 0.10f * (float) Math.sin(az * 5 + t * 0.45 + layer);
				bot[i] = dir(az, base, radius);
				mid[i] = dir(az, base + height * 0.35f, radius);
				top[i] = dir(az, base + height, radius);
				float c = 0.5f + 0.5f * (float) Math.sin(az * 2 + layer * 1.7 + t * 0.1);
				col[i] = mix(0x39FF9A, 0xB05CFF, c);
				float edge = (float) Math.sin(Math.PI * k); // fade the ends of the curtain
				alpha[i] = 0.40f * bright * edge * (0.6f + 0.4f * (float) Math.sin(az * 9 + t * 0.8));
			}
			for (int i = 0; i < n; i++) {
				quad2(vc, bot[i], argb(0, col[i]), mid[i], argb(alpha[i], col[i]), mid[i + 1], argb(alpha[i + 1], col[i + 1]), bot[i + 1], argb(0, col[i + 1]));
				quad2(vc, mid[i], argb(alpha[i], col[i]), top[i], argb(0, col[i]), top[i + 1], argb(0, col[i + 1]), mid[i + 1], argb(alpha[i + 1], col[i + 1]));
			}
		}
	}

	private static void meteors(VertexConsumer vc, float bright) {
		long now = System.nanoTime();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		if (now > nextMeteor) {
			nextMeteor = now + (long) (rnd.nextDouble(1.5, 6.0) * 1.0e9);
			Meteor mt = new Meteor();
			float[] p = dir((float) rnd.nextDouble(0, Math.PI * 2), (float) rnd.nextDouble(0.3, 1.2), 1f);
			// a random tangent direction that heads downwards
			float[] r = {(float) rnd.nextGaussian(), -Math.abs((float) rnd.nextGaussian()) - 0.3f, (float) rnd.nextGaussian()};
			float dot = r[0] * p[0] + r[1] * p[1] + r[2] * p[2];
			for (int i = 0; i < 3; i++) r[i] -= dot * p[i];
			float len = (float) Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
			for (int i = 0; i < 3; i++) {
				mt.p0[i] = p[i];
				mt.t0[i] = r[i] / len;
			}
			METEORS.add(mt);
		}
		final float life = 1.0f;
		final int seg = 10;
		METEORS.removeIf(mt -> (now - mt.start) / 1.0e9 > life);
		for (Meteor mt : METEORS) {
			float tau = (float) ((now - mt.start) / 1.0e9 / life);
			float env = (float) Math.sin(Math.PI * tau);
			float head = tau * 0.5f;
			float[][] pos = new float[seg + 1][];
			float[][] off = new float[seg + 1][];
			for (int k = 0; k <= seg; k++) {
				float th = Math.max(0f, head - k * 0.03f);
				float c = (float) Math.cos(th), s = (float) Math.sin(th);
				float[] p = new float[3];
				float[] tg = new float[3];
				for (int i = 0; i < 3; i++) {
					p[i] = mt.p0[i] * c + mt.t0[i] * s;
					tg[i] = -mt.p0[i] * s + mt.t0[i] * c;
				}
				float[] side = {p[1] * tg[2] - p[2] * tg[1], p[2] * tg[0] - p[0] * tg[2], p[0] * tg[1] - p[1] * tg[0]};
				float w = 0.010f * (1f - k / (float) seg);
				pos[k] = new float[]{p[0] * 44f, p[1] * 44f, p[2] * 44f};
				off[k] = new float[]{side[0] * w * 44f, side[1] * w * 44f, side[2] * w * 44f};
			}
			for (int k = 0; k < seg; k++) {
				float a0 = env * bright * (1f - k / (float) seg);
				float a1 = env * bright * (1f - (k + 1) / (float) seg);
				int c0 = argb(a0, mix(0xFFFFFF, 0x9CC8FF, k / (float) seg));
				int c1 = argb(a1, mix(0xFFFFFF, 0x9CC8FF, (k + 1) / (float) seg));
				float[] l0 = add(pos[k], off[k], 1), r0 = add(pos[k], off[k], -1);
				float[] l1 = add(pos[k + 1], off[k + 1], 1), r1 = add(pos[k + 1], off[k + 1], -1);
				quad2(vc, l0, c0, r0, c0, r1, c1, l1, c1);
			}
		}
	}

	private static float[] add(float[] p, float[] o, float sign) {
		return new float[]{p[0] + o[0] * sign, p[1] + o[1] * sign, p[2] + o[2] * sign};
	}

	private Cosmos() {
	}
}
