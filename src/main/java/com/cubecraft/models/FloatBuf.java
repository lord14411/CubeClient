package com.cubecraft.models;

import java.util.Arrays;

/** Growable float array. */
final class FloatBuf {
	float[] a = new float[256];
	int n = 0;

	void add(float v) {
		if (n == a.length) a = Arrays.copyOf(a, n * 2);
		a[n++] = v;
	}

	void add(float x, float y, float z) {
		add(x);
		add(y);
		add(z);
	}

	float[] toArray() {
		return Arrays.copyOf(a, n);
	}
}
