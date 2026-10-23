package com.cubecraft;

import com.cubecraft.models.MeshPart;
import com.cubecraft.models.ModelData;
import com.cubecraft.models.Xform;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws loaded models as real 3D geometry with the game's own entity render types (depth tested, textured, full bright).
 * <p>
 * Three hook points in the level renderer call {@link #hook(int)} once per frame each (see LevelRendererMixin).
 * Which of them exists, and which one sits at the right moment, depends on the game version, so the menu can
 * pick one (Solid A / B / C) and shows which ones fired. Auto uses the first one that fires.
 * Anything that throws turns solid drawing off and the particle hologram takes over.
 */
public final class ModelRender {
	private static final Logger LOG = LoggerFactory.getLogger("cubecraft");
	private static final String[] HOOK = {"A", "B", "C"};
	private static final int[] hits = new int[3];
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath("cubecraft", "textures/sky/white.png");
	private static final Matrix4f IDENTITY = new Matrix4f();
	private static final Map<MeshPart, RenderType> TYPES = new IdentityHashMap<>();

	private static boolean broken = false;
	private static String error = "";
	private static int frames = 0;
	private static long lastShowNs = 0;

	private static double camX, camY, camZ;
	private static boolean camSeen = false;
	private static float camYaw, camPitch;
	private static boolean rotSeen = false;

	// ------------------------------------------------------------ called from the mixins

	public static void onCameraPos(double x, double y, double z) {
		camX = x;
		camY = y;
		camZ = z;
		camSeen = true;
	}

	public static void onCameraRot(float yaw, float pitch) {
		camYaw = yaw;
		camPitch = pitch;
		rotSeen = true;
	}

	public static void hook(int id) {
		hits[id]++;
		if (broken || !wants(id)) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		try {
			if (draw(mc, id == 2)) frames++;
		} catch (Throwable t) {
			LOG.error("CubeCraft model renderer failed in solid hook " + HOOK[id], t);
			error = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
			broken = true;
			TYPES.clear();
		}
	}

	private static boolean wants(int id) {
		int mode = CubecraftConfig.modelRender;
		if (mode == 4) return false;
		if (mode >= 1 && mode <= 3) return id == mode - 1;
		for (int k = 0; k < hits.length; k++) {
			if (hits[k] > 0) return k == id; // auto: the first hook that has ever fired
		}
		return false;
	}

	// ------------------------------------------------------------ state for the menu / other classes

	/** True if a model was actually put on screen within the last second and a half. */
	public static boolean showing() {
		return System.nanoTime() - lastShowNs < 1_500_000_000L;
	}

	static void markShown() {
		lastShowNs = System.nanoTime();
	}

	/** Auto mode falls back to particles when solid drawing broke or no hook ever fired. */
	public static boolean hologramFallback() {
		return broken || (Models.activeTicks > 80 && hits[0] + hits[1] + hits[2] == 0);
	}

	/** Called when model files are re-read: drops the cached render types (their textures are replaced by id on the next draw). */
	static void forget() {
		TYPES.clear();
	}

	/** Lets the menu try solid drawing again after it failed. */
	public static void retry() {
		broken = false;
		error = "";
		frames = 0;
		TYPES.clear();
	}

	public static String[] status() {
		String mode = new String[]{"Auto", "Solid A", "Solid B", "Solid C", "Hologram"}[CubecraftConfig.modelRender];
		String l1;
		if (CubecraftConfig.modelRender == 4) l1 = "Hologram (particles)" + (ModelHologram.running() ? " - running" : "");
		else if (broken) l1 = "Solid failed: " + error;
		else if (hits[0] + hits[1] + hits[2] == 0) l1 = mode + ": waiting for a render hook...";
		else l1 = mode + (frames > 0 ? ": drawing (" + frames + " frames)" : ": hook ready, nothing to draw yet");
		String l2 = "Hooks fired A:" + (hits[0] > 0 ? "yes" : "no") + " B:" + (hits[1] > 0 ? "yes" : "no") + " C:" + (hits[2] > 0 ? "yes" : "no")
			+ (camSeen ? "" : " cam:fallback");
		return new String[]{l1, l2};
	}

	// ------------------------------------------------------------ drawing

	private static boolean draw(Minecraft mc, boolean rotate) {
		float pt = Models.partial(mc);
		List<Models.Inst> list = Models.collect(mc, pt);
		if (list.isEmpty()) return false;

		double cx, cy, cz;
		if (camSeen) {
			cx = camX;
			cy = camY;
			cz = camZ;
		} else { // first-person camera = your eyes
			var p = mc.player;
			cx = p.xo + (p.getX() - p.xo) * pt;
			cy = p.yo + (p.getY() - p.yo) * pt + p.getEyeHeight();
			cz = p.zo + (p.getZ() - p.zo) * pt;
		}
		Matrix4f view = rotate ? viewMatrix(mc, pt) : IDENTITY;
		MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
		boolean any = false;

		for (Models.Inst in : list) {
			Models.Entry e = Models.get(in.file());
			if (e.state != Models.READY) continue;
			double dx = in.x() - cx, dy = in.y() - cy, dz = in.z() - cz;
			if (dx * dx + dy * dy + dz * dz > 160.0 * 160.0) continue;
			ModelData d = e.data;
			ModelData.Norm n = d.norm[in.zup() ? 1 : 0];
			float rad = (float) Math.toRadians(in.yawDeg());
			float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
			for (int k = 0; k < d.parts.size(); k++) {
				MeshPart part = d.parts.get(k);
				RenderType rt = typeFor(in.file(), k, part);
				VertexConsumer vc = bs.getBuffer(rt);
				emit(vc, view, part, n, in, cos, sin, (float) dx, (float) dy, (float) dz);
				bs.endBatch(rt);
				any = true;
			}
		}
		if (any) markShown();
		return any;
	}

	/** For the hook that runs after the level renderer has popped the camera rotation off the matrix stack. */
	private static Matrix4f viewMatrix(Minecraft mc, float pt) {
		float yaw = rotSeen ? camYaw : mc.player.getViewYRot(pt);
		float pitch = rotSeen ? camPitch : mc.player.getViewXRot(pt);
		Quaternionf q = new Quaternionf().rotationYXZ((float) Math.PI - yaw * 0.017453292F, -pitch * 0.017453292F, 0f);
		return new Matrix4f().rotation(q.conjugate(new Quaternionf()));
	}

	private static RenderType typeFor(String file, int partIndex, MeshPart part) {
		RenderType t = TYPES.get(part);
		if (t != null) return t;
		Identifier id = WHITE;
		if (part.tex != null) {
			try {
				NativeImage img = NativeImage.read(new ByteArrayInputStream(part.tex.png()));
				// same id every time for the same model part, so reloading a model replaces its old texture
				final String path = "models/" + Integer.toHexString(file.hashCode()) + "_" + partIndex;
				id = Identifier.fromNamespaceAndPath("cubecraft", path);
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> path, img));
			} catch (IOException | RuntimeException e) {
				LOG.warn("[cubecraft] texture upload failed for " + file + ": " + e);
				id = WHITE;
			}
		}
		t = RenderTypes.entityCutoutNoCull(id);
		TYPES.put(part, t);
		return t;
	}

	/** Every triangle becomes a quad whose last two corners coincide, because the entity render types draw quads. */
	private static void emit(VertexConsumer vc, Matrix4f m, MeshPart part, ModelData.Norm n, Models.Inst in,
							 float cos, float sin, float rx, float ry, float rz) {
		float[] pos = part.pos, nrm = part.nrm, uv = part.uv;
		int color = 0xFF000000 | (part.tint & 0xFFFFFF);
		boolean zup = in.zup();
		float scale = in.scale();
		float[] o = new float[3], nn = new float[3];
		int tris = pos.length / 9;
		for (int t = 0; t < tris; t++) {
			for (int k = 0; k < 4; k++) {
				int j = Math.min(k, 2);
				int pi = t * 9 + j * 3;
				int ui = t * 6 + j * 2;
				Xform.point(n, zup, scale, cos, sin, pos[pi], pos[pi + 1], pos[pi + 2], o);
				Xform.dir(zup, cos, sin, nrm[pi], nrm[pi + 1], nrm[pi + 2], nn);
				vc.addVertex(m, o[0] + rx, o[1] + ry, o[2] + rz)
					.setColor(color)
					.setUv(uv[ui], uv[ui + 1])
					.setUv1(0, 10) // no hurt-flash overlay
					.setUv2(240, 240) // full brightness
					.setNormal(nn[0], nn[1], nn[2]);
			}
		}
	}

	private ModelRender() {
	}
}
