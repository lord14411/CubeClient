package com.cubecraft.models;

import java.util.List;
import java.util.Random;

/** A loaded model: its mesh parts plus the numbers needed to stand it on the ground at a sensible size. */
public final class ModelData {
	/** Where the model's centre / feet are and how much it is scaled so it is about player sized. */
	public static final class Norm {
		public final float scale, minY, cx, cz;

		Norm(float scale, float minY, float cx, float cz) {
			this.scale = scale;
			this.minY = minY;
			this.cx = cx;
			this.cz = cz;
		}
	}

	/** Random points on the surface (model space) with colours, for the particle hologram. */
	public static final class Points {
		public final int n;
		public final float[] xyz;
		public final int[] rgb;

		Points(int n, float[] xyz, int[] rgb) {
			this.n = n;
			this.xyz = xyz;
			this.rgb = rgb;
		}
	}

	private static final int POINTS = 4000;

	public final List<MeshPart> parts;
	public final int triangles;
	/** [0] = file is Y-up, [1] = file is Z-up. */
	public final Norm[] norm = new Norm[2];
	private Points points;

	public ModelData(List<MeshPart> parts) {
		this.parts = parts;
		int t = 0;
		for (MeshPart p : parts) t += p.pos.length / 9;
		this.triangles = t;
		norm[0] = computeNorm(false);
		norm[1] = computeNorm(true);
	}

	private Norm computeNorm(boolean zup) {
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (MeshPart p : parts) {
			float[] a = p.pos;
			for (int i = 0; i + 2 < a.length; i += 3) {
				float x = a[i], y = zup ? a[i + 2] : a[i + 1], z = zup ? -a[i + 1] : a[i + 2];
				if (x < minX) minX = x;
				if (x > maxX) maxX = x;
				if (y < minY) minY = y;
				if (y > maxY) maxY = y;
				if (z < minZ) minZ = z;
				if (z > maxZ) maxZ = z;
			}
		}
		if (minX > maxX) return new Norm(1f, 0f, 0f, 0f);
		float height = maxY - minY, longest = Math.max(maxX - minX, maxZ - minZ);
		float scale;
		// upright things become 1.8 blocks tall; things lying flat (or wider than tall) get their longest side fitted to 2 blocks
		if (height >= longest) scale = height > 1e-9f ? 1.8f / height : 1f;
		else scale = 2f / longest;
		if (!Float.isFinite(scale) || scale <= 0f) scale = 1f;
		return new Norm(scale, minY, (minX + maxX) / 2f, (minZ + maxZ) / 2f);
	}

	/** Surface sample used by the hologram, built once on first use. */
	public synchronized Points points() {
		if (points != null) return points;
		int total = 0;
		for (MeshPart p : parts) total += p.pos.length / 9;
		double[] cum = new double[total];
		int[] partOf = new int[total], triOf = new int[total];
		double sum = 0;
		int k = 0;
		for (int pi = 0; pi < parts.size(); pi++) {
			float[] a = parts.get(pi).pos;
			for (int t = 0; t < a.length / 9; t++, k++) {
				int o = t * 9;
				double ux = a[o + 3] - a[o], uy = a[o + 4] - a[o + 1], uz = a[o + 5] - a[o + 2];
				double vx = a[o + 6] - a[o], vy = a[o + 7] - a[o + 1], vz = a[o + 8] - a[o + 2];
				double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
				sum += 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
				cum[k] = sum;
				partOf[k] = pi;
				triOf[k] = t;
			}
		}
		if (total == 0 || sum <= 0) return points = new Points(0, new float[0], new int[0]);

		float[] xyz = new float[POINTS * 3];
		int[] rgb = new int[POINTS];
		Random rnd = new Random(12345);
		int n = 0;
		for (int s = 0; s < POINTS; s++) {
			for (int attempt = 0; attempt < 4; attempt++) {
				double pick = rnd.nextDouble() * sum;
				int lo = 0, hi = total - 1;
				while (lo < hi) {
					int mid = (lo + hi) >>> 1;
					if (cum[mid] < pick) lo = mid + 1;
					else hi = mid;
				}
				MeshPart p = parts.get(partOf[lo]);
				int t = triOf[lo];
				float r1 = (float) Math.sqrt(rnd.nextDouble()), r2 = rnd.nextFloat();
				float wa = 1f - r1, wb = r1 * (1f - r2), wc = r1 * r2;
				int o = t * 9, u = t * 6;
				float x = wa * p.pos[o] + wb * p.pos[o + 3] + wc * p.pos[o + 6];
				float y = wa * p.pos[o + 1] + wb * p.pos[o + 4] + wc * p.pos[o + 7];
				float z = wa * p.pos[o + 2] + wb * p.pos[o + 5] + wc * p.pos[o + 8];
				int colour = p.tint & 0xFFFFFF;
				if (p.tex != null) {
					float tu = wa * p.uv[u] + wb * p.uv[u + 2] + wc * p.uv[u + 4];
					float tv = wa * p.uv[u + 1] + wb * p.uv[u + 3] + wc * p.uv[u + 5];
					int c = p.tex.sample(tu, tv);
					if ((c >>> 24) < 128) continue; // transparent texel, try another spot
					int r = ((c >> 16) & 0xFF) * ((colour >> 16) & 0xFF) / 255;
					int g = ((c >> 8) & 0xFF) * ((colour >> 8) & 0xFF) / 255;
					int b = (c & 0xFF) * (colour & 0xFF) / 255;
					colour = (r << 16) | (g << 8) | b;
				}
				xyz[n * 3] = x;
				xyz[n * 3 + 1] = y;
				xyz[n * 3 + 2] = z;
				rgb[n] = colour;
				n++;
				break;
			}
		}
		return points = new Points(n, xyz, rgb);
	}
}
