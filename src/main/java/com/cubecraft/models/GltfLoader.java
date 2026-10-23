package com.cubecraft.models;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * glTF 2.0 (.glb and .gltf with external or embedded data). Node transforms are baked in; skinning and animation are ignored,
 * so rigged characters appear in their bind pose. Draco / meshopt compressed files are refused with a clear message.
 */
public final class GltfLoader {
	private static final int MAX_TRIS = 3_000_000;

	private static final class Builder {
		final FloatBuf pos = new FloatBuf(), nrm = new FloatBuf(), uv = new FloatBuf();
		final int material;

		Builder(int material) {
			this.material = material;
		}
	}

	private final Path dir;
	private Map<String, Object> root;
	private byte[] glbBin;
	private final Map<Integer, byte[]> buffers = new HashMap<>();
	private final Map<Integer, Tex> images = new HashMap<>();
	private final Map<Integer, Builder> builders = new LinkedHashMap<>();
	private int tris = 0;

	private GltfLoader(Path dir) {
		this.dir = dir;
	}

	public static ModelData load(Path file) throws IOException {
		try {
			return new GltfLoader(file.toAbsolutePath().getParent()).run(file);
		} catch (IOException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new IOException("could not read this glTF file: " + e);
		}
	}

	// ---------------------------------------------------------------- JSON helpers

	@SuppressWarnings("unchecked")
	private static Map<String, Object> obj(Object o) {
		return o instanceof Map ? (Map<String, Object>) o : null;
	}

	@SuppressWarnings("unchecked")
	private static List<Object> arr(Object o) {
		return o instanceof List ? (List<Object>) o : null;
	}

	private static int num(Object o, int def) {
		return o instanceof Double d ? (int) Math.round(d) : def;
	}

	private static Object at(List<Object> l, int i) throws IOException {
		if (l == null || i < 0 || i >= l.size()) throw new IOException("the file refers to something that is not there (index " + i + ")");
		return l.get(i);
	}

	// ---------------------------------------------------------------- reading the container

	private ModelData run(Path file) throws IOException {
		if (Files.size(file) > 500L * 1024 * 1024) throw new IOException("file is too big (over 500 MB)");
		byte[] all = Files.readAllBytes(file);
		String json;
		if (all.length >= 4 && all[0] == 'g' && all[1] == 'l' && all[2] == 'T' && all[3] == 'F') {
			ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
			if (all.length < 20) throw new IOException("corrupt GLB chunk");
			if (bb.getInt(4) != 2) throw new IOException("only glTF 2.0 is supported");
			long total = bb.getInt(8) & 0xFFFFFFFFL;
			if (total > all.length) throw new IOException("corrupt GLB chunk");
			int pos = 12;
			byte[] jsonBytes = null;
			while (pos + 8 <= total) {
				long len = bb.getInt(pos) & 0xFFFFFFFFL;
				int type = bb.getInt(pos + 4);
				int start = pos + 8;
				if (start + len > total) throw new IOException("corrupt GLB chunk");
				if (type == 0x4E4F534A && jsonBytes == null) {
					jsonBytes = java.util.Arrays.copyOfRange(all, start, start + (int) len);
				} else if (type == 0x004E4942 && glbBin == null) {
					glbBin = java.util.Arrays.copyOfRange(all, start, start + (int) len);
				}
				pos = start + (int) len;
			}
			if (jsonBytes == null) throw new IOException("corrupt GLB chunk (no JSON)");
			json = new String(jsonBytes, StandardCharsets.UTF_8);
		} else {
			json = new String(all, StandardCharsets.UTF_8);
		}
		root = obj(MiniJson.parse(json));
		if (root == null) throw new IOException("this is not a glTF file");

		List<Object> req = arr(root.get("extensionsRequired"));
		if (req != null) {
			for (Object e : req) {
				if ("KHR_draco_mesh_compression".equals(e) || "EXT_meshopt_compression".equals(e)) {
					throw new IOException("this glTF is compressed (" + e + "). Re-export it without compression.");
				}
			}
		}

		List<Object> nodes = arr(root.get("nodes"));
		List<Object> roots = new ArrayList<>();
		List<Object> scenes = arr(root.get("scenes"));
		if (scenes != null && !scenes.isEmpty()) {
			int si = num(root.get("scene"), 0);
			Map<String, Object> sc = obj(at(scenes, si >= 0 && si < scenes.size() ? si : 0));
			List<Object> sn = sc == null ? null : arr(sc.get("nodes"));
			if (sn != null) roots.addAll(sn);
		} else if (nodes != null) {
			boolean[] child = new boolean[nodes.size()];
			for (Object n : nodes) {
				Map<String, Object> m = obj(n);
				List<Object> ch = m == null ? null : arr(m.get("children"));
				if (ch != null) for (Object c : ch) if (num(c, -1) >= 0 && num(c, -1) < child.length) child[num(c, -1)] = true;
			}
			for (int i = 0; i < child.length; i++) if (!child[i]) roots.add((double) i);
		}
		float[] id = identity();
		for (Object r : roots) visit(num(r, -1), id, 0);

		if (tris == 0) throw new IOException("no triangles found in this glTF file");
		List<MeshPart> parts = new ArrayList<>();
		for (Builder b : builders.values()) {
			if (b.pos.n == 0) continue;
			int tint = 0xFFFFFF;
			Tex tex = null;
			Map<String, Object> mat = b.material >= 0 ? obj(listGet(arr(root.get("materials")), b.material)) : null;
			Map<String, Object> pbr = mat == null ? null : obj(mat.get("pbrMetallicRoughness"));
			if (pbr != null) {
				List<Object> f = arr(pbr.get("baseColorFactor"));
				if (f != null && f.size() >= 3) {
					int r = chan(f.get(0)), g = chan(f.get(1)), bl = chan(f.get(2));
					tint = (r << 16) | (g << 8) | bl;
				}
				Map<String, Object> bt = obj(pbr.get("baseColorTexture"));
				if (bt != null) tex = texture(num(bt.get("index"), -1));
			}
			parts.add(new MeshPart("material" + b.material, b.pos.toArray(), b.nrm.toArray(), b.uv.toArray(), tint, tex));
		}
		return new ModelData(parts);
	}

	private static Object listGet(List<Object> l, int i) {
		return l == null || i < 0 || i >= l.size() ? null : l.get(i);
	}

	private static int chan(Object o) {
		double d = o instanceof Double x ? x : 1.0;
		return Math.max(0, Math.min(255, (int) Math.round(d * 255)));
	}

	// ---------------------------------------------------------------- buffers, images

	private static String decodePercent(String s) {
		return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
	}

	/** Bytes behind a "uri": a data: URI or a file next to the model. */
	private byte[] readUri(String uri) throws IOException {
		if (uri.startsWith("data:")) {
			int comma = uri.indexOf(',');
			if (comma < 0) throw new IOException("bad data: URI");
			String head = uri.substring(5, comma);
			String body = uri.substring(comma + 1);
			if (head.endsWith(";base64")) {
				try {
					return Base64.getMimeDecoder().decode(body);
				} catch (IllegalArgumentException e) {
					throw new IOException("bad base64 data in the file");
				}
			}
			return decodePercent(body).getBytes(StandardCharsets.ISO_8859_1);
		}
		if (uri.contains("://")) throw new IOException("web addresses inside a model are not loaded: " + uri);
		Path p = dir.resolve(decodePercent(uri)).normalize();
		if (!p.startsWith(dir.normalize())) throw new IOException("file is outside the model folder: " + uri);
		if (!Files.isRegularFile(p)) throw new IOException("missing file: " + uri);
		return Files.readAllBytes(p);
	}

	private byte[] buffer(int i) throws IOException {
		byte[] b = buffers.get(i);
		if (b != null) return b;
		Map<String, Object> bo = obj(at(arr(root.get("buffers")), i));
		if (bo == null) throw new IOException("bad buffer " + i);
		Object uri = bo.get("uri");
		if (uri instanceof String s) b = readUri(s);
		else if (glbBin != null && i == 0) b = glbBin;
		else throw new IOException("buffer " + i + " has no data");
		if (b.length < num(bo.get("byteLength"), 0)) throw new IOException("a buffer is shorter than the file says (cut off?)");
		buffers.put(i, b);
		return b;
	}

	private Tex texture(int ti) {
		if (ti < 0) return null;
		try {
			Map<String, Object> t = obj(at(arr(root.get("textures")), ti));
			int src = t == null ? -1 : num(t.get("source"), -1);
			if (src < 0) return null;
			if (images.containsKey(src)) return images.get(src);
			Map<String, Object> im = obj(at(arr(root.get("images")), src));
			byte[] data = null;
			if (im.get("uri") instanceof String u) data = readUri(u);
			else if (im.get("bufferView") != null) {
				Map<String, Object> bv = obj(at(arr(root.get("bufferViews")), num(im.get("bufferView"), -1)));
				byte[] buf = buffer(num(bv.get("buffer"), 0));
				int off = num(bv.get("byteOffset"), 0), len = num(bv.get("byteLength"), 0);
				if (off < 0 || len < 0 || off + len > buf.length) throw new IOException("image data is cut off");
				data = java.util.Arrays.copyOfRange(buf, off, off + len);
			}
			Tex tex = data == null ? null : Tex.decode(data);
			images.put(src, tex);
			return tex;
		} catch (IOException | RuntimeException e) {
			return null; // a missing or unreadable picture only costs the texture, not the model
		}
	}

	// ---------------------------------------------------------------- accessors

	private static int comps(String type) throws IOException {
		return switch (type == null ? "" : type) {
			case "SCALAR" -> 1;
			case "VEC2" -> 2;
			case "VEC3" -> 3;
			case "VEC4" -> 4;
			default -> throw new IOException("unsupported accessor type " + type);
		};
	}

	private static int compSize(int ct) throws IOException {
		return switch (ct) {
			case 5120, 5121 -> 1;
			case 5122, 5123 -> 2;
			case 5125, 5126 -> 4;
			default -> throw new IOException("unsupported component type " + ct);
		};
	}

	private double read(ByteBuffer bb, int off, int ct, boolean normalized) {
		switch (ct) {
			case 5126:
				return bb.getFloat(off);
			case 5120: {
				byte v = bb.get(off);
				return normalized ? Math.max(v / 127.0, -1.0) : v;
			}
			case 5121: {
				int v = bb.get(off) & 0xFF;
				return normalized ? v / 255.0 : v;
			}
			case 5122: {
				short v = bb.getShort(off);
				return normalized ? Math.max(v / 32767.0, -1.0) : v;
			}
			case 5123: {
				int v = bb.getShort(off) & 0xFFFF;
				return normalized ? v / 65535.0 : v;
			}
			default:
				return bb.getInt(off) & 0xFFFFFFFFL;
		}
	}

	private double[] accessor(int idx, int expectComps) throws IOException {
		Map<String, Object> a = obj(at(arr(root.get("accessors")), idx));
		if (a == null) throw new IOException("bad accessor " + idx);
		int ct = num(a.get("componentType"), 5126), count = num(a.get("count"), 0);
		int nc = comps(a.get("type") instanceof String s ? s : null);
		if (expectComps > 0 && nc != expectComps) throw new IOException("accessor " + idx + " has the wrong shape");
		if (count < 0 || (long) count * nc > 60_000_000L) throw new IOException("accessor " + idx + " is too big");
		double[] out = new double[count * nc];
		if (a.get("bufferView") == null) return out; // all zeros by the spec
		boolean norm = Boolean.TRUE.equals(a.get("normalized"));
		Map<String, Object> bv = obj(at(arr(root.get("bufferViews")), num(a.get("bufferView"), -1)));
		byte[] buf = buffer(num(bv.get("buffer"), 0));
		int cs = compSize(ct);
		int stride = num(bv.get("byteStride"), 0);
		if (stride <= 0) stride = nc * cs;
		long start = (long) num(bv.get("byteOffset"), 0) + num(a.get("byteOffset"), 0);
		long viewEnd = (long) num(bv.get("byteOffset"), 0) + num(bv.get("byteLength"), 0);
		if (count > 0) {
			long last = start + (long) (count - 1) * stride + (long) nc * cs;
			if (start < 0 || last > buf.length || last > viewEnd) throw new IOException("accessor " + idx + " reads past the end of its data");
		}
		ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
		for (int e = 0; e < count; e++) {
			for (int c = 0; c < nc; c++) out[e * nc + c] = read(bb, (int) (start + (long) e * stride + (long) c * cs), ct, norm);
		}
		return out;
	}

	// ---------------------------------------------------------------- scene graph

	private static float[] identity() {
		return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
	}

	/** a * b, column-major. */
	private static float[] mul(float[] a, float[] b) {
		float[] r = new float[16];
		for (int c = 0; c < 4; c++) {
			for (int row = 0; row < 4; row++) {
				float s = 0;
				for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[c * 4 + k];
				r[c * 4 + row] = s;
			}
		}
		return r;
	}

	private static float[] local(Map<String, Object> node) {
		List<Object> m = arr(node.get("matrix"));
		if (m != null && m.size() == 16) {
			float[] r = new float[16];
			for (int i = 0; i < 16; i++) r[i] = (float) (m.get(i) instanceof Double d ? d : 0.0);
			return r;
		}
		float tx = 0, ty = 0, tz = 0, sx = 1, sy = 1, sz = 1, qx = 0, qy = 0, qz = 0, qw = 1;
		List<Object> t = arr(node.get("translation")), s = arr(node.get("scale")), q = arr(node.get("rotation"));
		if (t != null && t.size() >= 3) {
			tx = f(t.get(0));
			ty = f(t.get(1));
			tz = f(t.get(2));
		}
		if (s != null && s.size() >= 3) {
			sx = f(s.get(0));
			sy = f(s.get(1));
			sz = f(s.get(2));
		}
		if (q != null && q.size() >= 4) {
			qx = f(q.get(0));
			qy = f(q.get(1));
			qz = f(q.get(2));
			qw = f(q.get(3));
			float len = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
			if (len > 1e-12f) {
				qx /= len;
				qy /= len;
				qz /= len;
				qw /= len;
			} else qw = 1;
		}
		float xx = qx * qx, yy = qy * qy, zz = qz * qz, xy = qx * qy, xz = qx * qz, yz = qy * qz, wx = qw * qx, wy = qw * qy, wz = qw * qz;
		float r00 = 1 - 2 * (yy + zz), r01 = 2 * (xy - wz), r02 = 2 * (xz + wy);
		float r10 = 2 * (xy + wz), r11 = 1 - 2 * (xx + zz), r12 = 2 * (yz - wx);
		float r20 = 2 * (xz - wy), r21 = 2 * (yz + wx), r22 = 1 - 2 * (xx + yy);
		return new float[]{r00 * sx, r10 * sx, r20 * sx, 0, r01 * sy, r11 * sy, r21 * sy, 0, r02 * sz, r12 * sz, r22 * sz, 0, tx, ty, tz, 1};
	}

	private static float f(Object o) {
		return o instanceof Double d ? d.floatValue() : 0f;
	}

	private void visit(int ni, float[] parent, int depth) throws IOException {
		if (depth > 64) throw new IOException("nodes are nested too deeply");
		Map<String, Object> node = obj(at(arr(root.get("nodes")), ni));
		if (node == null) return;
		float[] world = mul(parent, local(node));
		int mesh = num(node.get("mesh"), -1);
		if (mesh >= 0) addMesh(mesh, world);
		List<Object> ch = arr(node.get("children"));
		if (ch != null) for (Object c : ch) visit(num(c, -1), world, depth + 1);
	}

	private void addMesh(int mi, float[] m) throws IOException {
		Map<String, Object> mesh = obj(at(arr(root.get("meshes")), mi));
		List<Object> prims = mesh == null ? null : arr(mesh.get("primitives"));
		if (prims == null) return;
		for (Object po : prims) {
			Map<String, Object> prim = obj(po);
			if (prim == null) continue;
			Map<String, Object> ext = obj(prim.get("extensions"));
			if (ext != null && ext.containsKey("KHR_draco_mesh_compression")) {
				throw new IOException("this glTF is compressed (KHR_draco_mesh_compression). Re-export it without compression.");
			}
			int mode = num(prim.get("mode"), 4);
			if (mode < 4 || mode > 6) continue; // points and lines
			Map<String, Object> at = obj(prim.get("attributes"));
			if (at == null || at.get("POSITION") == null) continue;
			double[] p = accessor(num(at.get("POSITION"), -1), 3);
			int count = p.length / 3;
			double[] n = at.get("NORMAL") != null ? accessor(num(at.get("NORMAL"), -1), 3) : null;
			double[] uv = at.get("TEXCOORD_0") != null ? accessor(num(at.get("TEXCOORD_0"), -1), 2) : null;
			if (n != null && n.length / 3 != count) n = null;
			if (uv != null && uv.length / 2 != count) uv = null;

			int[] idx;
			if (prim.get("indices") != null) {
				double[] d = accessor(num(prim.get("indices"), -1), 1);
				idx = new int[d.length];
				for (int i = 0; i < d.length; i++) idx[i] = (int) d[i];
			} else {
				idx = new int[count];
				for (int i = 0; i < count; i++) idx[i] = i;
			}
			for (int i : idx) if (i < 0 || i >= count) throw new IOException("a triangle uses a vertex that does not exist");

			// world-space positions and normals once per vertex
			float[] wp = new float[count * 3], wn = n == null ? null : new float[count * 3];
			float a = m[0], b = m[4], c = m[8], d = m[1], e = m[5], g = m[9], h = m[2], i2 = m[6], j = m[10];
			float cof00 = e * j - g * i2, cof01 = g * h - d * j, cof02 = d * i2 - e * h;
			float cof10 = c * i2 - b * j, cof11 = a * j - c * h, cof12 = b * h - a * i2;
			float cof20 = b * g - c * e, cof21 = c * d - a * g, cof22 = a * e - b * d;
			float det = a * cof00 + b * cof01 + c * cof02;
			float sign = det < 0 ? -1f : 1f;
			for (int v = 0; v < count; v++) {
				float x = (float) p[v * 3], y = (float) p[v * 3 + 1], z = (float) p[v * 3 + 2];
				wp[v * 3] = m[0] * x + m[4] * y + m[8] * z + m[12];
				wp[v * 3 + 1] = m[1] * x + m[5] * y + m[9] * z + m[13];
				wp[v * 3 + 2] = m[2] * x + m[6] * y + m[10] * z + m[14];
				if (wn != null) {
					float nx = (float) n[v * 3], ny = (float) n[v * 3 + 1], nz = (float) n[v * 3 + 2];
					float ox = sign * (cof00 * nx + cof01 * ny + cof02 * nz);
					float oy = sign * (cof10 * nx + cof11 * ny + cof12 * nz);
					float oz = sign * (cof20 * nx + cof21 * ny + cof22 * nz);
					float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
					if (len < 1e-12f) {
						ox = 0;
						oy = 1;
						oz = 0;
					} else {
						ox /= len;
						oy /= len;
						oz /= len;
					}
					wn[v * 3] = ox;
					wn[v * 3 + 1] = oy;
					wn[v * 3 + 2] = oz;
				}
			}

			int mat = num(prim.get("material"), -1);
			Builder bld = builders.computeIfAbsent(mat, Builder::new);
			int len = idx.length;
			if (mode == 4) {
				for (int i = 0; i + 2 < len; i += 3) tri(bld, wp, wn, uv, idx[i], idx[i + 1], idx[i + 2]);
			} else if (mode == 5) {
				for (int i = 0; i + 2 < len; i++) {
					if ((i & 1) == 0) tri(bld, wp, wn, uv, idx[i], idx[i + 1], idx[i + 2]);
					else tri(bld, wp, wn, uv, idx[i + 1], idx[i], idx[i + 2]);
				}
			} else {
				for (int i = 1; i + 1 < len; i++) tri(bld, wp, wn, uv, idx[0], idx[i], idx[i + 1]);
			}
		}
	}

	private void tri(Builder b, float[] wp, float[] wn, double[] uv, int i0, int i1, int i2) throws IOException {
		if (++tris > MAX_TRIS) throw new IOException("model has too many triangles (limit 3 million)");
		int[] v = {i0, i1, i2};
		for (int i : v) b.pos.add(wp[i * 3], wp[i * 3 + 1], wp[i * 3 + 2]);
		if (wn != null) {
			for (int i : v) b.nrm.add(wn[i * 3], wn[i * 3 + 1], wn[i * 3 + 2]);
		} else {
			float ax = wp[i0 * 3], ay = wp[i0 * 3 + 1], az = wp[i0 * 3 + 2];
			float ux = wp[i1 * 3] - ax, uy = wp[i1 * 3 + 1] - ay, uz = wp[i1 * 3 + 2] - az;
			float vx = wp[i2 * 3] - ax, vy = wp[i2 * 3 + 1] - ay, vz = wp[i2 * 3 + 2] - az;
			float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
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
		for (int i : v) {
			if (uv != null) {
				b.uv.add((float) uv[i * 2]);
				b.uv.add((float) uv[i * 2 + 1]);
			} else {
				b.uv.add(0f);
				b.uv.add(0f);
			}
		}
	}
}
