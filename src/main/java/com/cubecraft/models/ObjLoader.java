package com.cubecraft.models;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.stream.Stream;

/** Wavefront .obj (+ .mtl and textures). Quads and polygons are split into triangles; V is flipped for textures. */
public final class ObjLoader {
	private static final int MAX_TRIS = 3_000_000;

	private static final class Mat {
		int kd = 0xFFFFFF;
		String map;
	}

	private static final class Builder {
		final String name;
		final FloatBuf pos = new FloatBuf(), nrm = new FloatBuf(), uv = new FloatBuf();

		Builder(String name) {
			this.name = name;
		}
	}

	public static ModelData load(Path file) throws IOException {
		try {
			return parse(file);
		} catch (IOException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new IOException("could not read this OBJ file: " + e);
		}
	}

	private static ModelData parse(Path file) throws IOException {
		if (Files.size(file) > 400L * 1024 * 1024) throw new IOException("file is too big (over 400 MB)");
		Path dir = file.toAbsolutePath().getParent();
		FloatBuf v = new FloatBuf(), vt = new FloatBuf(), vn = new FloatBuf();
		Map<String, Builder> builders = new LinkedHashMap<>();
		Map<String, Mat> mats = new HashMap<>();
		Map<String, Path> files = null;
		Builder cur = null;
		int tris = 0;

		try (BufferedReader r = new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.ISO_8859_1))) {
			String line;
			while ((line = r.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.charAt(0) == '#') continue;
				int sp = line.indexOf(' ');
				if (sp < 0) continue;
				String key = line.substring(0, sp);
				String rest = line.substring(sp + 1).trim();
				switch (key) {
					case "v" -> {
						float[] f = floats(rest, 3);
						if (f != null) v.add(f[0], f[1], f[2]);
					}
					case "vt" -> {
						float[] f = floats(rest, 2);
						if (f != null) {
							vt.add(f[0]);
							vt.add(f[1]);
						}
					}
					case "vn" -> {
						float[] f = floats(rest, 3);
						if (f != null) vn.add(f[0], f[1], f[2]);
					}
					case "mtllib" -> {
						if (files == null) files = index(dir);
						for (String n : mtlNames(rest, files)) readMtl(files.get(n), mats);
					}
					case "usemtl" -> cur = builders.computeIfAbsent(rest, Builder::new);
					case "f" -> {
						if (cur == null) cur = builders.computeIfAbsent("", Builder::new);
						StringTokenizer st = new StringTokenizer(rest);
						int n = st.countTokens();
						if (n < 3) continue;
						int[] vi = new int[n], ti = new int[n], ni = new int[n];
						for (int k = 0; k < n; k++) {
							String tok = st.nextToken();
							String[] sl = tok.split("/", -1);
							vi[k] = index(sl[0], v.n / 3, true);
							ti[k] = sl.length > 1 ? index(sl[1], vt.n / 2, false) : -1;
							ni[k] = sl.length > 2 ? index(sl[2], vn.n / 3, false) : -1;
						}
						for (int k = 1; k + 1 < n; k++) {
							emit(cur, v, vt, vn, vi[0], ti[0], ni[0], vi[k], ti[k], ni[k], vi[k + 1], ti[k + 1], ni[k + 1]);
							if (++tris > MAX_TRIS) throw new IOException("model has too many triangles (limit 3 million)");
						}
					}
					default -> {
					}
				}
			}
		}
		if (tris == 0) throw new IOException("no faces found in this OBJ file");

		if (files == null) files = index(dir);
		Map<Path, Tex> texCache = new HashMap<>();
		List<MeshPart> parts = new ArrayList<>();
		boolean anyTex = false;
		List<Builder> list = new ArrayList<>(builders.values());
		Tex[] texes = new Tex[list.size()];
		for (int i = 0; i < list.size(); i++) {
			Mat m = mats.get(list.get(i).name);
			if (m != null && m.map != null) {
				Path tp = findFile(m.map, files);
				if (tp != null) texes[i] = texCache.computeIfAbsent(tp, ObjLoader::readTex);
			}
			if (texes[i] != null) anyTex = true;
		}
		Tex guess = null;
		if (!anyTex) { // no usable material: look for a picture next to the model with the same name
			String base = baseName(file.getFileName().toString());
			for (String ext : new String[]{"png", "jpg", "jpeg"}) {
				Path tp = files.get(base + "." + ext);
				if (tp != null) {
					guess = texCache.computeIfAbsent(tp, ObjLoader::readTex);
					if (guess != null) break;
				}
			}
		}
		for (int i = 0; i < list.size(); i++) {
			Builder b = list.get(i);
			if (b.pos.n == 0) continue;
			Mat m = mats.get(b.name);
			Tex tex = texes[i] != null ? texes[i] : guess;
			int tint = tex != null ? 0xFFFFFF : (m != null ? m.kd : 0xFFFFFF);
			parts.add(new MeshPart(b.name, b.pos.toArray(), b.nrm.toArray(), b.uv.toArray(), tint, tex));
		}
		return new ModelData(parts);
	}

	private static float[] floats(String s, int need) {
		StringTokenizer st = new StringTokenizer(s);
		if (st.countTokens() < need) return null;
		float[] f = new float[need];
		try {
			for (int i = 0; i < need; i++) f[i] = Float.parseFloat(st.nextToken());
		} catch (NumberFormatException e) {
			return null;
		}
		return f;
	}

	/** OBJ index (1-based, or negative = from the end) to a 0-based one. Vertices must exist, uv / normals may be missing. */
	private static int index(String tok, int count, boolean required) throws IOException {
		if (tok.isEmpty()) {
			if (required) throw new IOException("face has an empty vertex index");
			return -1;
		}
		int i;
		try {
			i = Integer.parseInt(tok);
		} catch (NumberFormatException e) {
			if (required) throw new IOException("face has a bad vertex index: " + tok);
			return -1;
		}
		int z = i < 0 ? count + i : i - 1;
		if (i == 0 || z < 0 || z >= count) {
			if (required) throw new IOException("face refers to a missing vertex (index " + i + ")");
			return -1;
		}
		return z;
	}

	private static void emit(Builder b, FloatBuf v, FloatBuf vt, FloatBuf vn,
							 int v0, int t0, int n0, int v1, int t1, int n1, int v2, int t2, int n2) {
		float[] p = v.a;
		float ax = p[v0 * 3], ay = p[v0 * 3 + 1], az = p[v0 * 3 + 2];
		float bx = p[v1 * 3], by = p[v1 * 3 + 1], bz = p[v1 * 3 + 2];
		float cx = p[v2 * 3], cy = p[v2 * 3 + 1], cz = p[v2 * 3 + 2];
		b.pos.add(ax, ay, az);
		b.pos.add(bx, by, bz);
		b.pos.add(cx, cy, cz);
		if (n0 >= 0 && n1 >= 0 && n2 >= 0) {
			float[] n = vn.a;
			b.nrm.add(n[n0 * 3], n[n0 * 3 + 1], n[n0 * 3 + 2]);
			b.nrm.add(n[n1 * 3], n[n1 * 3 + 1], n[n1 * 3 + 2]);
			b.nrm.add(n[n2 * 3], n[n2 * 3 + 1], n[n2 * 3 + 2]);
		} else {
			float ux = bx - ax, uy = by - ay, uz = bz - az, wx = cx - ax, wy = cy - ay, wz = cz - az;
			float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len < 1e-12f) {
				nx = 0;
				ny = 1;
				nz = 0;
			} else {
				nx /= len;
				ny /= len;
				nz /= len;
			}
			for (int k = 0; k < 3; k++) b.nrm.add(nx, ny, nz);
		}
		int[] ts = {t0, t1, t2};
		for (int t : ts) {
			if (t >= 0) {
				b.uv.add(vt.a[t * 2]);
				b.uv.add(1f - vt.a[t * 2 + 1]);
			} else {
				b.uv.add(0f);
				b.uv.add(0f);
			}
		}
	}

	// ---------------------------------------------------------------- materials and files

	/** lower-case file name -> path for everything in the model's folder and up to two levels below. */
	private static Map<String, Path> index(Path dir) {
		Map<String, Path> m = new HashMap<>();
		try (Stream<Path> s = Files.walk(dir, 3)) {
			s.filter(Files::isRegularFile).limit(5000).forEach(p -> m.putIfAbsent(p.getFileName().toString().toLowerCase(Locale.ROOT), p));
		} catch (IOException ignored) {
		}
		return m;
	}

	private static List<String> mtlNames(String rest, Map<String, Path> files) {
		List<String> out = new ArrayList<>();
		String whole = lastSegment(rest).toLowerCase(Locale.ROOT);
		if (files.containsKey(whole)) {
			out.add(whole);
			return out;
		}
		for (String t : rest.split("\\s+")) {
			String k = lastSegment(t).toLowerCase(Locale.ROOT);
			if (files.containsKey(k)) out.add(k);
		}
		return out;
	}

	private static void readMtl(Path p, Map<String, Mat> mats) {
		Mat cur = null;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(Files.newInputStream(p), StandardCharsets.ISO_8859_1))) {
			String line;
			while ((line = r.readLine()) != null) {
				line = line.trim();
				int sp = line.indexOf(' ');
				if (sp < 0 || line.charAt(0) == '#') continue;
				String key = line.substring(0, sp).toLowerCase(Locale.ROOT);
				String rest = line.substring(sp + 1).trim();
				switch (key) {
					case "newmtl" -> {
						cur = new Mat();
						mats.put(rest, cur);
					}
					case "kd" -> {
						float[] f = floats(rest, 3);
						if (cur != null && f != null) {
							int rr = Math.max(0, Math.min(255, Math.round(f[0] * 255)));
							int gg = Math.max(0, Math.min(255, Math.round(f[1] * 255)));
							int bb = Math.max(0, Math.min(255, Math.round(f[2] * 255)));
							cur.kd = (rr << 16) | (gg << 8) | bb;
						}
					}
					case "map_kd" -> {
						if (cur != null) cur.map = stripOptions(rest);
					}
					default -> {
					}
				}
			}
		} catch (IOException | RuntimeException ignored) {
		}
	}

	/** map_Kd lines may start with options such as "-s 1 1 1 -o 0 0"; what is left is the file name. */
	private static String stripOptions(String s) {
		String[] t = s.split("\\s+");
		int i = 0;
		while (i < t.length && t[i].startsWith("-")) {
			String o = t[i].toLowerCase(Locale.ROOT);
			int args = switch (o) {
				case "-s", "-o", "-t" -> 3;
				case "-mm" -> 2;
				case "-bm", "-boost", "-texres", "-clamp", "-blendu", "-blendv", "-imfchan", "-type" -> 1;
				default -> 0;
			};
			i += 1 + args;
		}
		StringBuilder b = new StringBuilder();
		for (; i < t.length; i++) b.append(b.length() > 0 ? " " : "").append(t[i]);
		return b.toString().isEmpty() ? s : b.toString();
	}

	private static String lastSegment(String path) {
		String p = path.replace('\\', '/');
		return p.substring(p.lastIndexOf('/') + 1);
	}

	private static String baseName(String name) {
		int d = name.lastIndexOf('.');
		return (d > 0 ? name.substring(0, d) : name).toLowerCase(Locale.ROOT);
	}

	/** Finds a texture by its last name segment, ignoring case and folders (works with Windows paths from the exporter). */
	private static Path findFile(String name, Map<String, Path> files) {
		return files.get(lastSegment(name).toLowerCase(Locale.ROOT));
	}

	private static Tex readTex(Path p) {
		try {
			return Tex.decode(Files.readAllBytes(p));
		} catch (IOException e) {
			return null;
		}
	}

	private ObjLoader() {
	}
}
