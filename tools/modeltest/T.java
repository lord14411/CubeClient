import com.cubecraft.models.*;
import java.nio.file.*;

public class T {
	static int fails = 0;
	static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }
	static boolean near(float a, float b) { return Math.abs(a - b) < 1e-3f; }

	public static void main(String[] a) throws Exception {
		Path A = Paths.get("assets");

		ModelData guy = ObjLoader.load(A.resolve("obj/guy.obj"));
		check(guy.triangles == 12, "guy.obj: 6 quads -> 12 triangles (got " + guy.triangles + ")");
		check(guy.parts.size() == 1, "guy.obj: one material part");
		check(guy.parts.get(0).tex != null, "guy.obj: texture found through a Windows path + wrong case");
		ModelData.Norm n = guy.norm[0];
		check(near(n.scale, 0.9f), "guy.obj: 2-tall model scales to 1.8 (scale " + n.scale + ")");
		check(near(n.minY, 0f) && near(n.cx, 0f) && near(n.cz, 0f), "guy.obj: feet at 0, centred");
		float[] o = new float[3];
		Xform.point(n, false, 1f, 1f, 0f, 0.5f, 2f, 0.5f, o);
		check(near(o[0], 0.45f) && near(o[1], 1.8f) && near(o[2], 0.45f), "Xform top corner -> (0.45,1.8,0.45) got " + java.util.Arrays.toString(o));
		Xform.point(n, false, 1f, 0f, 1f, 0.5f, 0f, 0.0f, o);
		check(near(o[0], 0f) && near(o[2], -0.45f), "Xform yaw 90deg rotates x into -z: " + java.util.Arrays.toString(o));
		check(near(guy.parts.get(0).uv[1], 1f), "guy.obj: V is flipped for textures");
		ModelData.Points pts = guy.points();
		check(pts.n > 3000, "guy.obj: hologram points generated (" + pts.n + ")");
		boolean colours = false; for (int i = 0; i < pts.n; i++) if (pts.rgb[i] != 0xFFFFFF && pts.rgb[i] != 0) colours = true;
		check(colours, "guy.obj: hologram points carry texture colours");

		ModelData pole = ObjLoader.load(A.resolve("obj/pole.obj"));
		check(pole.parts.get(0).tex != null, "pole.obj: texture guessed from pole.png");
		check(near(pole.norm[0].scale, 2f / 3f), "pole.obj: Y-up reading is a long flat thing -> fitted by length (scale " + pole.norm[0].scale + ")");
		check(near(pole.norm[1].scale, 1.8f / 3f), "pole.obj: Z-up reading stands it up and fits height (scale " + pole.norm[1].scale + ")");
		// Z-up: the pole's top (z=3) must end up 1.8 high, feet at 0
		Xform.point(pole.norm[1], true, 1f, 1f, 0f, 0.05f, 0.05f, 3f, o);
		check(near(o[1], 1.8f) && near(o[0], 0f) && near(o[2], 0f), "pole.obj: Z-up top -> (0,1.8,0) got " + java.util.Arrays.toString(o));

		ModelData q = GltfLoader.load(A.resolve("glb/quad.glb"));
		check(q.triangles == 2, "quad.glb: strip of 4 -> 2 triangles (got " + q.triangles + ")");
		check(q.parts.get(0).tex != null, "quad.glb: embedded JPEG decoded");
		check((q.parts.get(0).tex.argb[0] >>> 24) == 255, "quad.glb: opaque texture");
		float[] p = q.parts.get(0).pos;
		boolean allZ5 = true; for (int i = 2; i < p.length; i += 3) if (!near(p[i], 5f)) allZ5 = false;
		check(allZ5, "quad.glb: parent translation applied (z=5)");
		boolean found = false; for (int i = 0; i < p.length; i += 3) if (near(p[i], -2f) && near(p[i + 1], 2f)) found = true;
		check(found, "quad.glb: rotation+scale composed in the right order");
		float[] nr = q.parts.get(0).nrm;
		check(near(nr[2], 1f), "quad.glb: normals survive the transform (" + nr[0] + "," + nr[1] + "," + nr[2] + ")");
		ModelData.Points qp = q.points();
		check(qp.n > 3000, "quad.glb: hologram points (" + qp.n + ")");

		ModelData ext = GltfLoader.load(A.resolve("glb/ext.gltf"));
		check(ext.triangles == 2 && ext.parts.get(0).tex != null, "ext.gltf: external .bin + image file + percent-encoded name");
		ModelData b64 = GltfLoader.load(A.resolve("glb/b64.gltf"));
		check(b64.triangles == 2 && b64.parts.get(0).tex != null, "b64.gltf: data: URIs for buffer and image");

		for (String bad : new String[]{"glb/draco.glb", "glb/trunc.glb", "glb/escape.gltf", "obj/garbage.obj", "obj/badidx.obj"}) {
			try {
				ModelData d = bad.endsWith("obj") ? ObjLoader.load(A.resolve(bad)) : GltfLoader.load(A.resolve(bad));
				check(false, bad + " should have failed but loaded " + d.triangles + " tris");
			} catch (java.io.IOException e) {
				check(true, bad + " rejected cleanly: " + e.getMessage());
			}
		}
		System.out.println(fails == 0 ? "\nALL PASSED" : "\n" + fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
