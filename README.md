# CubeCraft Studio (Fabric, Minecraft 1.21.11)

1. Create a new GitHub repo and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab. The "Build mod" workflow runs on every push (Gradle 9.2.0 + Loom 1.14.10).
3. When it is green, download the **cubecraft-jar** artifact and put `cubecraft-studio-0.5.0.jar`
   (not the `-sources` one) in `.minecraft/mods` with Fabric Loader and Fabric API for 1.21.11.

## Keys (rebind under Controls > CubeCraft Studio)
- **Right Shift** - open the menu (or `/cubecraft`)
- **C** (hold) - zoom
- **G** - repeat last emote

## Menu tabs (everything is client-side - no op or cheats needed)
World (client time lock + client weather), Skins (18 built-in), Sky & Fog (RGB sliders + 12 presets incl. Galaxy,
Nebula, Aurora, Vapor), Cosmos (real nebula skyboxes with stars, aurora curtains and meteors), Emotes (8, `/cubecraft emote <name>`),
Extras (fullbright, reset). Settings are saved to `config/cubecraft.properties`.

## v0.6 - Cosmos sky (Minecraft 1.21.11)
Pick a sky under **Cosmos**: Violet, Gold, Crimson, Emerald, Deep Space (or a Galaxy/Nebula/Aurora/Blood Moon preset in Sky & Fog).
Picking one also locks your client time to midnight so the terrain matches; press *Unlock time* in World to undo it.
The line at the bottom of the Cosmos tab says **Sky running** once the sky hook fired. If it says *Waiting for the sky hook*
or *Sky failed*, send `logs/latest.log` (search for `cubecraft`).

Sky textures are generated, not hand painted: see `tools/skygen/README.md` to tweak colours or make new skies.

## v0.7 - Custom skins
Open the **Custom** tab. Put 64x64 skin PNGs in `.minecraft/config/cubecraft/skins/` and press Reload.
Name a file `something_slim.png` for slim (Alex-style) arms. Old 64x32 skins are not supported.

## v0.8 - 3D models
Open the **Models** tab. Put model files in `.minecraft/config/cubecraft/models/` (subfolders are fine) and press **Rescan**.
Supported: `.glb` (best choice), `.gltf` (with its .bin and picture files next to it, or embedded) and `.obj` (with `.mtl` and textures).
Pick a model, then:
- **Place in front** stands it in the world 2.5 blocks ahead, facing you (up to 24, **Remove last** / **Clear all** undo them).
- **Wear** shows it on you in third person (F5); **Hide body** makes your own body invisible while it is worn.
- **Size**, **Turn** and **Z-up** are remembered per model. Turn on **Z-up** if a model lies on its back (Blender / CAD files).
- Models are scaled to be about player sized (1.8 blocks tall, or 2 blocks long if lying flat).

**Draw** picks how it is drawn: Auto, Solid A/B/C (real 3D through different render hooks) or Hologram (coloured particles, always works).
The two lines at the bottom of the tab show what is running. If nothing appears, press Draw to try the next one and send `logs/latest.log` (search for `cubecraft`).

Limits: models do not animate (rigged characters stand in their bind pose); Draco / meshopt compressed glTF is refused with a message
(re-export without compression); webp and other textures Java cannot read show as plain colour; only you see models.
Models from other games usually need converting to glb in Blender first, and many only allow personal use, so do not ship them inside the mod.
`samples/` has a small test model, and `tools/modeltest/` has the loader tests.

## GUI refresh
The menu has a new look: gradient frame, animated header, sidebar with colored tab icons, rounded buttons with hover glow, on/off switches, colored RGB sliders and a twinkling star background. Only `CubecraftScreen.java` changed (it repaints the normal buttons, so every button works exactly as before).
