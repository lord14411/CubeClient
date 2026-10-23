"""Procedural cube-map sky generator (nebula + milky way + stars). Needs numpy and Pillow."""
import numpy as np

FACES = ["px", "nx", "py", "ny", "pz", "nz"]
C = np.array([[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]], float)
U = np.array([[0, 1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [0, 1, 0], [0, 1, 0]], float)
R = np.cross(C, U)  # direction = C + s*R - t*U, s,t in [-1,1] (t grows downwards in the image)


def face_dirs(i, S):
    g = np.linspace(-1, 1, S)  # texel centres sit exactly on the face edge -> no seams
    s, t = np.meshgrid(g, g)
    d = C[i][None, None, :] + s[..., None] * R[i] - t[..., None] * U[i]
    d /= np.linalg.norm(d, axis=-1, keepdims=True)
    return d.astype(np.float32)


# ------------------------------------------------------------------ noise
_rng = np.random.default_rng(12345)
LAT = _rng.random((64, 64, 64), dtype=np.float32)


def _rot():
    q, _ = np.linalg.qr(_rng.normal(size=(3, 3)))
    return q.astype(np.float32)


ROT = [_rot() for _ in range(48)]
OFF = [(_rng.random(3) * 60).astype(np.float32) for _ in range(48)]


def vnoise(x, y, z):
    xi = np.floor(x); yi = np.floor(y); zi = np.floor(z)
    fx = x - xi; fy = y - yi; fz = z - zi
    fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz)
    xi = xi.astype(np.int32) & 63; yi = yi.astype(np.int32) & 63; zi = zi.astype(np.int32) & 63
    x1 = (xi + 1) & 63; y1 = (yi + 1) & 63; z1 = (zi + 1) & 63
    c00 = LAT[xi, yi, zi] * (1 - fx) + LAT[x1, yi, zi] * fx
    c10 = LAT[xi, y1, zi] * (1 - fx) + LAT[x1, y1, zi] * fx
    c01 = LAT[xi, yi, z1] * (1 - fx) + LAT[x1, yi, z1] * fx
    c11 = LAT[xi, y1, z1] * (1 - fx) + LAT[x1, y1, z1] * fx
    return (c00 * (1 - fy) + c10 * fy) * (1 - fz) + (c01 * (1 - fy) + c11 * fy) * fz


def fbm(x, y, z, octaves, seed=0, gain=0.5):
    tot = np.zeros_like(x); amp = 1.0; norm = 0.0; f = 1.0
    for o in range(octaves):
        k = (seed * 5 + o) % 48
        m = ROT[k]; off = OFF[k]
        xr = (m[0, 0] * x + m[0, 1] * y + m[0, 2] * z) * f + off[0]
        yr = (m[1, 0] * x + m[1, 1] * y + m[1, 2] * z) * f + off[1]
        zr = (m[2, 0] * x + m[2, 1] * y + m[2, 2] * z) * f + off[2]
        tot += amp * vnoise(xr, yr, zr)
        norm += amp; amp *= gain; f *= 2.03
    return tot / norm


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def mix3(c0, c1, c2, t):
    t = t[..., None]
    c0, c1, c2 = (np.asarray(c, np.float32) for c in (c0, c1, c2))
    a = c0 * (1 - 2 * np.clip(t, 0, .5)) + c1 * 2 * np.clip(t, 0, .5)
    b = c1 * (1 - 2 * np.clip(t - .5, 0, .5)) + c2 * 2 * np.clip(t - .5, 0, .5)
    return np.where(t < .5, a, b)


# ------------------------------------------------------------------ nebula
def _fields(d, P):
    x, y, z = d[..., 0], d[..., 1], d[..., 2]
    sc = P["scale"]; s = P["seed"]
    W = P["warp"]
    wx = fbm(x * sc * .8, y * sc * .8, z * sc * .8, 3, s + 1) - .5
    wy = fbm(x * sc * .8 + 5, y * sc * .8, z * sc * .8, 3, s + 2) - .5
    wz = fbm(x * sc * .8, y * sc * .8 + 9, z * sc * .8, 3, s + 3) - .5
    px = x * sc + W * wx * 2.4; py = y * sc + W * wy * 2.4; pz = z * sc + W * wz * 2.4
    gas = fbm(px, py, pz, 7, s + 4)
    fil = 1 - np.abs(2 * fbm(px * 1.6, py * 1.6, pz * 1.6, 4, s + 5) - 1)
    col_t = fbm(px * .6, py * .6, pz * .6, 3, s + 6)
    dust = fbm(px * 1.3, py * 1.3, pz * 1.3, 5, s + 7)
    reg = fbm(x * P["region_scale"], y * P["region_scale"], z * P["region_scale"], 3, s + 8)
    haze = fbm(x * 2.2, y * 2.2, z * 2.2, 4, s + 9)
    return gas, fil, col_t, dust, reg, haze


def calibrate(P, n=60000):
    r = np.random.default_rng(P["seed"])
    v = r.normal(size=(n, 3)).astype(np.float32)
    v /= np.linalg.norm(v, axis=1, keepdims=True)
    d = v.reshape(n, 1, 3)
    gas, fil, col_t, dust, reg, haze = [a.ravel() for a in _fields(d, P)]
    cal = {}
    cal["gas_lo"] = np.quantile(gas, 1 - P["cover"])
    cal["gas_hi"] = np.quantile(gas, 1 - P["cover"] * 0.12)
    cal["col"] = (np.quantile(col_t, .08), np.quantile(col_t, .92))
    cal["dust"] = (np.quantile(dust, .55), np.quantile(dust, .92))
    cal["reg"] = (np.quantile(reg, .25), np.quantile(reg, .8))
    return cal


def nebula_face(i, S, P, cal, dither_seed=0):
    d = face_dirs(i, S)
    x, y, z = d[..., 0], d[..., 1], d[..., 2]
    gas, fil, col_t, dust, reg, haze = _fields(d, P)
    region = smooth(cal["reg"][0], cal["reg"][1], reg)
    dens = smooth(cal["gas_lo"], cal["gas_hi"], gas)
    dens = dens * (P["region_floor"] + (1 - P["region_floor"]) * region)
    dens = (dens ** 1.25) * (0.5 + 0.8 * fil)
    t = np.clip((col_t - cal["col"][0]) / (cal["col"][1] - cal["col"][0]), 0, 1)
    col = mix3(P["c0"], P["c1"], P["c2"], t)
    neb = dens[..., None] * col * P["neb_i"]
    neb += (np.clip(dens, 0, 1.3) ** 3)[..., None] * np.asarray(P["hi"], np.float32) * P["hi_i"]
    dk = smooth(cal["dust"][0], cal["dust"][1], dust)
    neb *= (1 - 0.8 * dk * np.clip(dens * 1.6, 0, 1))[..., None]

    # milky way band
    n = np.asarray(P["band"], np.float32); n /= np.linalg.norm(n)
    lat = x * n[0] + y * n[1] + z * n[2]
    bw = np.exp(-(lat / P["band_w"]) ** 2)
    mw = bw * (0.35 + 0.9 * haze) * (1 - 0.65 * dk * bw)
    img = neb + mw[..., None] * np.asarray(P["mw"], np.float32) * P["mw_i"]

    # horizon glow
    el = y
    hz = np.exp(-np.maximum(el, 0) / P["hz_h"]) * np.exp(np.minimum(el, 0) * 3.0)
    hz *= 0.65 + 0.7 * fbm(x * 3.0, y * 0.5, z * 3.0, 4, P["seed"] + 11)
    img += hz[..., None] * np.asarray(P["hz"], np.float32) * P["hz_i"]
    img += np.asarray(P["base"], np.float32)

    img = 1 - np.exp(-img * P["expo"])
    img = np.clip(img, 0, 1) ** (1 / 1.08)
    r = np.random.default_rng(dither_seed + i)
    img += (r.random(img.shape, dtype=np.float32) - .5) / 255.0
    return (np.clip(img, 0, 1) * 255 + .5).astype(np.uint8)


# ------------------------------------------------------------------ stars
def star_layer(S, seed=7, band=(0.35, 0.8, 0.5), band_w=0.2, scale=1.0):
    rng = np.random.default_rng(seed)
    k = (S / 1536.0) ** 2
    n = np.asarray(band, np.float32); n /= np.linalg.norm(n)
    groups = [  # count, Imin, Imax, sigma, band boost
        (int(19000 * scale), 0.25, 0.60, 0.70, 0.6),
        (int(3200 * scale), 0.50, 0.85, 0.80, 0.4),
        (int(380 * scale), 0.85, 1.00, 1.00, 0.2),
        (int(46 * scale), 1.00, 1.00, 1.5, 0.0),
    ]
    dirs, inten, sig, cols = [], [], [], []
    for cnt, i0, i1, sg, boost in groups:
        m = int(cnt * 1.8)
        v = rng.normal(size=(m, 3)).astype(np.float32)
        v /= np.linalg.norm(v, axis=1, keepdims=True)
        lat = v @ n
        p = (1 - boost) * 0.45 + boost * np.exp(-(lat / band_w) ** 2) * 1.6
        keep = rng.random(m) < np.clip(p, 0.05, 1)
        v = v[keep][:cnt]
        c = len(v)
        I = i0 + (i1 - i0) * rng.random(c) ** 1.6
        temp = rng.random(c)
        col = np.where(temp[:, None] < .25, [0.72, 0.82, 1.0],
              np.where(temp[:, None] < .8, [1.0, 1.0, 1.0], [1.0, 0.84, 0.66])).astype(np.float32)
        dirs.append(v); inten.append(I); sig.append(np.full(c, sg)); cols.append(col)
    D = np.concatenate(dirs); I = np.concatenate(inten); SG = np.concatenate(sig); CL = np.concatenate(cols)

    out = {}
    for fi, name in enumerate(FACES):
        dc = D @ C[fi]
        sel = dc > 0.55
        d = D[sel]; dcs = dc[sel]
        s = (d @ R[fi]) / dcs
        t = -(d @ U[fi]) / dcs
        ok = (np.abs(s) <= 1.02) & (np.abs(t) <= 1.02)
        s, t = s[ok], t[ok]
        Ii, Sg, Cl = I[sel][ok], SG[sel][ok], CL[sel][ok]
        px = (s + 1) / 2 * (S - 1); py = (t + 1) / 2 * (S - 1)
        acc = np.zeros((S, S, 4), np.float32)
        base_x = np.floor(px).astype(int); base_y = np.floor(py).astype(int)
        for oy in range(-3, 5):
            for ox in range(-3, 5):
                ix = base_x + ox; iy = base_y + oy
                m = (ix >= 0) & (ix < S) & (iy >= 0) & (iy < S)
                if not m.any():
                    continue
                dx = px[m] - ix[m]; dy = py[m] - iy[m]
                w = Ii[m] * np.exp(-(dx * dx + dy * dy) / (2 * Sg[m] ** 2))
                np.add.at(acc[..., 0], (iy[m], ix[m]), w * Cl[m][:, 0])
                np.add.at(acc[..., 1], (iy[m], ix[m]), w * Cl[m][:, 1])
                np.add.at(acc[..., 2], (iy[m], ix[m]), w * Cl[m][:, 2])
                np.add.at(acc[..., 3], (iy[m], ix[m]), w)
        a = np.clip(acc[..., 3], 0, 1)
        rgb = acc[..., :3] / np.maximum(acc[..., 3:4], 1e-6)
        rgb = np.clip(rgb, 0, 1)
        out[name] = np.dstack([(rgb * 255 + .5).astype(np.uint8), (a * 255 + .5).astype(np.uint8)])
    return out


# ------------------------------------------------------------------ preview (approximates the in-game cube sky)
def sample_cube(faces, d):
    out = np.zeros(d.shape[:-1] + (faces[FACES[0]].shape[-1],), np.float32)
    S = faces[FACES[0]].shape[0]
    for fi, name in enumerate(FACES):
        dc = d @ C[fi]
        m = (dc > 0) & (np.abs(d @ R[fi]) <= dc * 1.0001) & (np.abs(d @ U[fi]) <= dc * 1.0001)
        s = (d[m] @ R[fi]) / dc[m]; t = -(d[m] @ U[fi]) / dc[m]
        ix = np.clip(((s + 1) / 2 * (S - 1) + .5).astype(int), 0, S - 1)
        iy = np.clip(((t + 1) / 2 * (S - 1) + .5).astype(int), 0, S - 1)
        out[m] = faces[name][iy, ix].astype(np.float32) / 255
    return out


def view_dirs(w, h, fov_v, yaw, pitch):
    f = np.tan(np.radians(fov_v) / 2)
    xs = (np.arange(w) + .5) / w * 2 - 1; ys = 1 - (np.arange(h) + .5) / h * 2
    X, Y = np.meshgrid(xs * f * w / h, ys * f)
    v = np.stack([X, Y, -np.ones_like(X)], -1)  # camera looks down -z
    cp, sp = np.cos(np.radians(pitch)), np.sin(np.radians(pitch))
    cy, sy = np.cos(np.radians(yaw)), np.sin(np.radians(yaw))
    Rp = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    v = v @ Rp.T @ Ry.T
    return (v / np.linalg.norm(v, axis=-1, keepdims=True)).astype(np.float32)


def compose(neb, stars, d):
    n = sample_cube({k: v for k, v in neb.items()}, d)
    s = sample_cube(stars, d)
    rgb = s[..., :3]; a = s[..., 3:4]
    return (np.clip(n * (1 - a) + rgb * a, 0, 1) * 255).astype(np.uint8)
