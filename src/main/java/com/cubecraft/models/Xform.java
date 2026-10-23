package com.cubecraft.models;

/**
 * Model space to world offsets: optional Z-up fix, stand on the feet, centre, size, then turn about Y.
 * cos/sin describe the turn (x' = x cos + z sin, z' = -x sin + z cos), so at 0 the model's front (+Z) faces +Z.
 */
public final class Xform {
	public static void point(ModelData.Norm n, boolean zup, float scale, float cos, float sin,
							 float x, float y, float z, float[] out) {
		float px = x, py = y, pz = z;
		if (zup) { // (x, y, z) -> (x, z, -y): Blender's up and front become Minecraft's
			py = z;
			pz = -y;
		}
		float s = n.scale * scale;
		float ax = (px - n.cx) * s, ay = (py - n.minY) * s, az = (pz - n.cz) * s;
		out[0] = ax * cos + az * sin;
		out[1] = ay;
		out[2] = -ax * sin + az * cos;
	}

	/** Same turn for normals (no move, no size). */
	public static void dir(boolean zup, float cos, float sin, float x, float y, float z, float[] out) {
		float px = x, py = y, pz = z;
		if (zup) {
			py = z;
			pz = -y;
		}
		out[0] = px * cos + pz * sin;
		out[1] = py;
		out[2] = -px * sin + pz * cos;
	}

	private Xform() {
	}
}
