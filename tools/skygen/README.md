# Sky texture generator

The skies in `src/main/resources/assets/cubecraft/textures/sky/` are generated procedurally (needs `numpy` and `Pillow`).

- `presets.py` - colours and cloud coverage per sky. Edit these, or add a new entry.
- `skygen.py` - noise, nebula, milky way and star code.
- `generate.py` - writes the PNGs: `python3 generate.py` (all) or `python3 generate.py violet stars`.

`generate.py` prints a `horizon 0x......` colour per sky: copy it into the `FOG` array in `Cosmos.java`, and add the
name to `NAMES`/`KEY` there if you made a new sky.
