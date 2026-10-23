"""Sky presets. Colours are linear 0..1 RGB. Edit and re-run generate.py to make your own."""

ORDER = ["violet", "gold", "crimson", "emerald", "deep"]

_common = dict(scale=1.9, warp=1.25, region_scale=0.9, region_floor=0.30, cover=0.50,
               neb_i=0.80, hi_i=0.30, band_w=0.20, mw_i=0.30, hz_h=0.16, hz_i=0.55, expo=1.0)

PRESETS = {
    # purple galaxy with a glowing magenta horizon (screenshots 1 and 2)
    "violet": dict(_common, seed=3,
                   c0=(0.10, 0.05, 0.42), c1=(0.60, 0.14, 0.75), c2=(0.95, 0.40, 0.80),
                   hi=(1.0, 0.75, 0.95), mw=(0.45, 0.30, 0.80), band=(0.35, 0.8, 0.5),
                   hz=(0.65, 0.15, 0.75), base=(0.006, 0.004, 0.018)),
    # warm cream / gold cloud over orange, dark brown dust (screenshot 4)
    "gold": dict(_common, seed=9, cover=0.46,
                 c0=(0.30, 0.10, 0.04), c1=(0.95, 0.45, 0.14), c2=(1.00, 0.85, 0.55),
                 hi=(1.0, 0.95, 0.80), mw=(0.55, 0.38, 0.22), band=(0.6, 0.6, 0.5),
                 hz=(0.60, 0.28, 0.10), base=(0.005, 0.004, 0.006)),
    # red / orange burning sky (screenshot 3)
    "crimson": dict(_common, seed=17, cover=0.55, neb_i=0.75,
                    c0=(0.35, 0.02, 0.03), c1=(0.95, 0.22, 0.05), c2=(1.0, 0.62, 0.12),
                    hi=(1.0, 0.85, 0.35), mw=(0.55, 0.15, 0.10), band=(-0.4, 0.8, 0.4),
                    hz=(0.75, 0.12, 0.05), base=(0.012, 0.002, 0.002)),
    "emerald": dict(_common, seed=23, cover=0.48,
                    c0=(0.02, 0.18, 0.22), c1=(0.08, 0.62, 0.55), c2=(0.50, 0.95, 0.70),
                    hi=(0.85, 1.0, 0.90), mw=(0.20, 0.55, 0.55), band=(0.2, 0.9, -0.4),
                    hz=(0.10, 0.55, 0.50), base=(0.002, 0.008, 0.012)),
    # almost black with a strong milky way and a few blue clouds
    "deep": dict(_common, seed=31, cover=0.34, neb_i=0.7, mw_i=0.7, band_w=0.24,
                 c0=(0.04, 0.06, 0.25), c1=(0.15, 0.30, 0.75), c2=(0.55, 0.65, 1.0),
                 hi=(0.80, 0.88, 1.0), mw=(0.55, 0.60, 0.90), band=(0.3, 0.7, 0.65),
                 hz=(0.18, 0.28, 0.65), hz_i=0.45, base=(0.003, 0.004, 0.012)),
}
