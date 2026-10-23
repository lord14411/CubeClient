package com.cubecraft.models;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiny JSON reader (objects, arrays, strings, numbers, booleans, null) so the loaders need no library. */
final class MiniJson {
	private final String s;
	private int i = 0;

	private MiniJson(String s) {
		this.s = s;
	}

	static Object parse(String text) throws IOException {
		MiniJson p = new MiniJson(text);
		p.ws();
		Object v = p.value(0);
		p.ws();
		if (p.i != p.s.length()) throw p.err("extra data");
		return v;
	}

	private IOException err(String m) {
		return new IOException("bad JSON (" + m + " at " + i + ")");
	}

	private void ws() {
		while (i < s.length() && s.charAt(i) <= ' ') i++;
	}

	private Object value(int depth) throws IOException {
		if (depth > 200) throw err("too deep");
		if (i >= s.length()) throw err("unexpected end");
		char c = s.charAt(i);
		if (c == '{') {
			i++;
			Map<String, Object> m = new LinkedHashMap<>();
			ws();
			if (peek('}')) return m;
			while (true) {
				ws();
				if (i >= s.length() || s.charAt(i) != '"') throw err("expected key");
				String k = string();
				ws();
				expect(':');
				ws();
				m.put(k, value(depth + 1));
				ws();
				if (peek('}')) return m;
				expect(',');
			}
		}
		if (c == '[') {
			i++;
			List<Object> l = new ArrayList<>();
			ws();
			if (peek(']')) return l;
			while (true) {
				ws();
				l.add(value(depth + 1));
				ws();
				if (peek(']')) return l;
				expect(',');
			}
		}
		if (c == '"') return string();
		if (s.startsWith("true", i)) {
			i += 4;
			return Boolean.TRUE;
		}
		if (s.startsWith("false", i)) {
			i += 5;
			return Boolean.FALSE;
		}
		if (s.startsWith("null", i)) {
			i += 4;
			return null;
		}
		int st = i;
		while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
		if (st == i) throw err("unexpected character");
		try {
			return Double.parseDouble(s.substring(st, i));
		} catch (NumberFormatException e) {
			throw err("bad number");
		}
	}

	private boolean peek(char c) {
		if (i < s.length() && s.charAt(i) == c) {
			i++;
			return true;
		}
		return false;
	}

	private void expect(char c) throws IOException {
		if (!peek(c)) throw err("expected '" + c + "'");
	}

	private String string() throws IOException {
		i++; // opening quote
		StringBuilder b = new StringBuilder();
		while (i < s.length()) {
			char c = s.charAt(i++);
			if (c == '"') return b.toString();
			if (c == '\\') {
				if (i >= s.length()) break;
				char e = s.charAt(i++);
				switch (e) {
					case 'n' -> b.append('\n');
					case 't' -> b.append('\t');
					case 'r' -> b.append('\r');
					case 'b' -> b.append('\b');
					case 'f' -> b.append('\f');
					case 'u' -> {
						if (i + 4 > s.length()) throw err("bad escape");
						try {
							b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
						} catch (NumberFormatException ex) {
							throw err("bad escape");
						}
						i += 4;
					}
					default -> b.append(e);
				}
			} else b.append(c);
		}
		throw err("unterminated string");
	}
}
