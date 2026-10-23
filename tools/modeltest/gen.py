import struct, json, io, math, base64
from PIL import Image

# ---------- OBJ: 1x2x1 box (quads, one face with negative indices), mtl with a Windows path in the wrong case
img = Image.new("RGBA", (4, 4))
for y in range(4):
    for x in range(4):
        img.putpixel((x, y), (255 if x < 2 else 0, 128, 255 if y < 2 else 0, 255))
img.save("assets/obj/guy.png")
open("assets/obj/guy.mtl", "w").write("newmtl skin\nKd 1 1 1\nmap_Kd C:\\\\Users\\\\x\\\\Desktop\\\\GUY.PNG\n")
v = [(-.5,0,-.5),(.5,0,-.5),(.5,0,.5),(-.5,0,.5),(-.5,2,-.5),(.5,2,-.5),(.5,2,.5),(-.5,2,.5)]
lines = ["# test", "mtllib guy.mtl", "o guy", "usemtl skin"]
lines += ["v %f %f %f" % p for p in v]
lines += ["vt 0 0", "vt 1 0", "vt 1 1", "vt 0 1", "vn 0 1 0"]
quads = [(1,2,3,4),(5,8,7,6),(1,5,6,2),(2,6,7,3),(3,7,8,4),(4,8,5,1)]
for q in quads[:5]:
    lines.append("f " + " ".join("%d/%d/1" % (q[i], i + 1) for i in range(4)))
lines.append("f -8//1 -7//1 -6//1 -5//1")
open("assets/obj/guy.obj", "w").write("\n".join(lines) + "\n")

# Z-up pole 0.1 x 0.1 x 3 with no mtl but a same-named png
lines = ["v 0 0 0","v 0.1 0 0","v 0.1 0.1 0","v 0 0.1 0","v 0 0 3","v 0.1 0 3","v 0.1 0.1 3","v 0 0.1 3",
         "vt 0 0","vt 1 0","vt 1 1",
         "f 1/1 2/2 3/3","f 5/1 6/2 7/3","f 1/1 2/2 6/3","f 1 6 5"]
open("assets/obj/pole.obj", "w").write("\n".join(lines) + "\n")
Image.new("RGB", (2, 2), (10, 200, 30)).save("assets/obj/pole.png")

# ---------- GLB: parent scaled 2x + moved up 5, child rotated 90deg about Z; textured quad as a triangle strip
def pad(b, ch=b'\0'): return b + ch * ((4 - len(b) % 4) % 4)
pos = [(-1,0,0),(1,0,0),(-1,1,0),(1,1,0)]
uv = [(0,1),(1,1),(0,0),(1,0)]
nrm = [(0,0,1)] * 4
idx = [0,1,2,3]
bin_ = b""
def add(data):
    global bin_
    off = len(bin_); bin_ += pad(data); return off, len(data)
pb = b"".join(struct.pack("<3f", *p) for p in pos)
nb = b"".join(struct.pack("<3f", *p) for p in nrm)
ub = b"".join(struct.pack("<2f", *p) for p in uv)
ib = b"".join(struct.pack("<H", i) for i in idx)
jb = io.BytesIO(); Image.new("RGB", (8, 8), (255, 0, 0)).save(jb, "JPEG"); jb = jb.getvalue()
o1,l1 = add(pb); o2,l2 = add(nb); o3,l3 = add(ub); o4,l4 = add(ib); o5,l5 = add(jb)
g = {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],
 "nodes":[{"translation":[0,0,5],"scale":[2,2,2],"children":[1]},{"mesh":0,"rotation":[0,0,math.sin(math.pi/4),math.cos(math.pi/4)]}],
 "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"TEXCOORD_0":2},"indices":3,"material":0,"mode":5}]}],
 "materials":[{"pbrMetallicRoughness":{"baseColorTexture":{"index":0},"baseColorFactor":[1,1,1,1]},"alphaMode":"OPAQUE"}],
 "textures":[{"source":0}],"images":[{"bufferView":4,"mimeType":"image/jpeg"}],
 "accessors":[{"bufferView":0,"componentType":5126,"count":4,"type":"VEC3","min":[-1,0,0],"max":[1,1,0]},
  {"bufferView":1,"componentType":5126,"count":4,"type":"VEC3"},
  {"bufferView":2,"componentType":5126,"count":4,"type":"VEC2"},
  {"bufferView":3,"componentType":5123,"count":4,"type":"SCALAR"}],
 "bufferViews":[{"buffer":0,"byteOffset":o1,"byteLength":l1},{"buffer":0,"byteOffset":o2,"byteLength":l2},
  {"buffer":0,"byteOffset":o3,"byteLength":l3},{"buffer":0,"byteOffset":o4,"byteLength":l4},
  {"buffer":0,"byteOffset":o5,"byteLength":l5}],
 "buffers":[{"byteLength":len(bin_)}]}
js = pad(json.dumps(g).encode(), b' ')
glb = struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(bin_)) + struct.pack("<II", len(js), 0x4E4F534A) + js + struct.pack("<II", len(bin_), 0x004E4942) + bin_
open("assets/glb/quad.glb", "wb").write(glb)

g2 = dict(g); g2["extensionsRequired"] = ["KHR_draco_mesh_compression"]
js2 = pad(json.dumps(g2).encode(), b' ')
open("assets/glb/draco.glb", "wb").write(struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js2)) + struct.pack("<II", len(js2), 0x4E4F534A) + js2)
open("assets/glb/trunc.glb", "wb").write(glb[:len(glb) - 200])
open("assets/obj/garbage.obj", "w").write("v 1 2\nf 1 2 3\n")
open("assets/obj/badidx.obj", "w").write("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 9\n")

# ---------- .gltf with external bin + png (percent-encoded names with spaces) and with data: URIs
pos = [(0,0,0),(1,0,0),(0,1,0),(1,1,0)]
pb = b"".join(struct.pack("<3f", *p) for p in pos); ib = b"".join(struct.pack("<I", i) for i in [0,1,2,2,1,3])
data = pb + ib
im = io.BytesIO(); Image.new("RGBA", (2, 2), (0, 255, 0, 255)).save(im, "PNG")
def gltf(buri, iuri):
    return {"asset":{"version":"2.0"},"nodes":[{"mesh":0}],
     "meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1,"material":0}]}],
     "materials":[{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}],"textures":[{"source":0}],"images":[{"uri":iuri}],
     "accessors":[{"bufferView":0,"componentType":5126,"count":4,"type":"VEC3"},{"bufferView":1,"componentType":5125,"count":6,"type":"SCALAR"}],
     "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":len(pb)},{"buffer":0,"byteOffset":len(pb),"byteLength":len(ib)}],
     "buffers":[{"byteLength":len(data),"uri":buri}]}
open("assets/glb/my model.bin", "wb").write(data); open("assets/glb/my tex.png", "wb").write(im.getvalue())
json.dump(gltf("my%20model.bin", "my%20tex.png"), open("assets/glb/ext.gltf", "w"))
json.dump(gltf("data:application/octet-stream;base64," + base64.b64encode(data).decode(), "data:image/png;base64," + base64.b64encode(im.getvalue()).decode()), open("assets/glb/b64.gltf", "w"))
# path escaping out of the folder must be refused
json.dump(gltf("../secret.bin", "my%20tex.png"), open("assets/glb/escape.gltf", "w"))
print("assets ok")
