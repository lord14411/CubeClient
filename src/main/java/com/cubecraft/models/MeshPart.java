package com.cubecraft.models;

/** All triangles that share one material. Arrays are un-indexed: 9 floats (3 corners) per triangle for pos/nrm, 6 for uv. */
public final class MeshPart {
	public final String name;
	public final float[] pos, nrm, uv;
	/** RGB multiplier (material colour). */
	public final int tint;
	public final Tex tex;

	public MeshPart(String name, float[] pos, float[] nrm, float[] uv, int tint, Tex tex) {
		this.name = name;
		this.pos = pos;
		this.nrm = nrm;
		this.uv = uv;
		this.tint = tint;
		this.tex = tex;
	}
}
