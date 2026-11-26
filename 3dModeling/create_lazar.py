# Blender 3.6+ / 4.x. Run in Scripting > Run Script.
import bpy, math, os
from mathutils import Vector
from math import sin, cos, pi, sqrt
bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
for c in list(bpy.data.collections):
    if c.name!='Collection' and c.users==0: bpy.data.collections.remove(c)
scene=bpy.context.scene
scene.unit_settings.system='METRIC'
root=bpy.data.collections.new('Lazarus 1461 | reconstruction'); scene.collection.children.link(root)
def group(name):
    c=bpy.data.collections.new(name); root.children.link(c); return c
walls=group('01 Masonry'); details=group('02 Facade'); roofs=group('03 Roof and drum'); openings=group('04 Openings'); stage=group('05 Presentation')
def mat(name,color,rough=.85,metal=0):
    m=bpy.data.materials.new(name); m.diffuse_color=(*color,1); m.use_nodes=True; p=m.node_tree.nodes.get('Principled BSDF'); p.inputs['Base Color'].default_value=(*color,1); p.inputs['Roughness'].default_value=rough; p.inputs['Metallic'].default_value=metal; return m
plaster=mat('Warm lime plaster',(.68,.61,.49)); brick=mat('Exposed red brick',(.34,.15,.085)); trim=mat('Limewashed relief',(.76,.70,.58)); stone=mat('Foundation stone',(.29,.28,.25)); roof=mat('Weathered dark roof',(.095,.12,.13),.68,.18); dark=mat('Recess shadow',(.035,.028,.022)); wood=mat('Oak doors',(.16,.075,.035)); gold=mat('Aged gilding',(.53,.34,.09),.38,.65)
def mesh(name,v,f,m,c):
    me=bpy.data.meshes.new(name); me.from_pydata(v,[],f); me.update(); o=bpy.data.objects.new(name,me); c.objects.link(o); o.data.materials.append(m); return o
def box(name,loc,dim,m,c):
    x,y,z=loc; a,b,d=[i/2 for i in dim]; v=[(x+i*a,y+j*b,z+k*d) for k in (-1,1) for j in (-1,1) for i in (-1,1)]; return mesh(name,v,[(0,2,3,1),(4,5,7,6),(0,1,5,4),(1,3,7,5),(3,2,6,7),(2,0,4,6)],m,c)
def prism(name,poly,z0,z1,m,c):
    n=len(poly); return mesh(name,[(x,y,z) for z in (z0,z1) for x,y in poly],[tuple(reversed(range(n))),tuple(range(n,2*n))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)],m,c)
def lathe(name,profile,m,c,n=64,center=(0,0)):
    v=[(center[0]+r*cos(2*pi*i/n),center[1]+r*sin(2*pi*i/n),z) for r,z in profile for i in range(n)]; f=[(j*n+i,j*n+(i+1)%n,(j+1)*n+(i+1)%n,(j+1)*n+i) for j in range(len(profile)-1) for i in range(n)]; f += [tuple(reversed(range(n))),tuple(range((len(profile)-1)*n,len(profile)*n))]; o=mesh(name,v,f,m,c); return o
# East +Y, west -Y. Relative dimensions estimated from measured elevation.
W=6.4; D=8.1; H=9.5
box('Main rectangular volume',(0,0,H/2),(W,D,H),plaster,walls)
box('Foundation plinth',(0,0,.15),(W+.18,D+.18,.3),stone,walls)
# One semicircular eastern apse, lower than main volume
poly=[(2.12*cos(pi*i/32),D/2+2.95*sin(pi*i/32)) for i in range(33)]
prism('Eastern apse',poly,0,5.95,plaster,walls)
prism('Apse foundation',[(2.18*cos(pi*i/32),D/2+3.01*sin(pi*i/32)) for i in range(33)],0,.24,stone,walls)
# Facade coordinate conversion: u across face, d projects outward
faces=[('West',(0,-D/2),(1,0),(0,-1),W),('South',(-W/2,0),(0,-1),(-1,0),D),('North',(W/2,0),(0,1),(1,0),D),('East',(0,D/2),(-1,0),(0,1),W)]
def point(face,u,d,z):
    _,(x,y),(tx,ty),(nx,ny),_=face; return (x+tx*u+nx*d,y+ty*u+ny*d,z)
def panel(name,face,poly,depth0,depth1,m,c):
    n=len(poly); v=[point(face,u,d,z) for d in (depth0,depth1) for u,z in poly]; return mesh(name,v,[tuple(reversed(range(n))),tuple(range(n,2*n))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)],m,c)
def rect(name,face,u,z,w,h,d0,d1,m,c): return panel(name,face,[(u-w/2,z),(u+w/2,z),(u+w/2,z+h),(u-w/2,z+h)],d0,d1,m,c)
def archpoly(u,z,w,h):
    r=w/2; spring=z+h-r; return [(u-r,z),(u+r,z)]+[(u+r*cos(pi*i/24),spring+r*sin(pi*i/24)) for i in range(25)]
def archband(name,face,u,spring,r,t,d0,d1,m):
    p=[(u+r*cos(pi*i/32),spring+r*sin(pi*i/32)) for i in range(33)]+[(u+(r-t)*cos(pi*i/32),spring+(r-t)*sin(pi*i/32)) for i in reversed(range(33))]; panel(name,face,p,d0,d1,m,details)
def window(face,u,z,w,h,door=False):
    name=face[0]+(' portal' if door else ' window')
    panel(name,face,archpoly(u,z,w,h),.012,.035,wood if door else dark,openings)
    r=w/2+.14; spring=z+h-w/2
    archband(name+' archivolt',face,u,spring,r,.14,.025,.18,brick)
    for s in (-1,1): rect(name+' jamb',face,u+s*(w/2+.07),z,.14,spring-z,.02,.18,brick,details)
    if not door: rect(name+' sill',face,u,z-.1,w+.18,.1,.02,.22,trim,details)
    if door:
        for i in range(1,5): rect(name+' plank joint',face,u-w/2+i*w/5,z+.03,.022,h-w/2-.03,.036,.045,dark,openings)
for face in faces:
    name,*_,width=face
    # Tripartite relief facade, high central rounded field and lower side fields
    for u in (-width/2+.18,-width*.19,width*.19,width/2-.18):
        rect(name+' pilaster',face,u,.24,.34,7.65,.0,.23,trim,details)
    # Central gabled/rounded masonry crown visible in Strukov drawing
    r=width*.19; spring=7.82
    panel(name+' central raised crown',face,archpoly(0,7.65,2*r,1.9),-.25,.24,plaster,details)
    archband(name+' central facade arch',face,0,spring,r,.18,.23,.36,brick)
    for s in (-1,1):
        u=s*width*.345; rr=width*.155
        archband(name+' side facade arch',face,u,7.55,rr,.15,.02,.23,trim)
    rect(name+' upper brick cornice',face,0,9.12,width,.16,0,.15,brick,details)
    if name=='West':
        window(face,0,.28,1.10,2.65,True); window(face,0,3.6,.95,2.35); window(face,0,7.1,.55,.95)
    elif name in ('South','North'):
        window(face,0,2.8,.43,1.0); window(face,0,6.45,.62,1.65)
# roof: four pitched planes, near low pyramidal profile in Strukov / survey
x=W/2+.25; y=D/2+.25; z=9.48; top=10.65
mesh('Four slope roof',[(-x,-y,z),(x,-y,z),(x,y,z),(-x,y,z),(0,-.65,top),(0,.65,top)],[(0,1,4),(1,2,5,4),(2,3,5),(3,0,4,5),(3,2,1,0)],roof,roofs)
# Apse roof slopes towards curved outer edge
v=[(2.22*cos(pi*i/40),D/2+3.08*sin(pi*i/40),5.96) for i in range(41)]+[(0,D/2,8.15)]
mesh('Apse half-conical roof',v,[(i,i+1,41) for i in range(40)],roof,roofs)
# Drum with banded cornice and narrow recessed-looking arches
lathe('Cylindrical drum',[(1.20,10.35),(1.20,12.85)],plaster,roofs)
for z,r,h in [(10.5,1.28,.13),(12.64,1.30,.12),(12.83,1.36,.12),(12.98,1.36,.12)]: lathe('Drum cornice',[(r,z),(r,z+h)],brick,roofs)
for i in range(8):
    a=2*pi*i/8; face=('Drum '+str(i),(1.20*cos(a),1.20*sin(a)),(-sin(a),cos(a)),(cos(a),sin(a)),1); window(face,0,10.92,.35,1.37)
lathe('Bell shaped cupola',[(1.38,13.05),(1.32,13.25),(1.16,13.6),(.92,14.0),(.63,14.32),(.31,14.50),(.23,14.55)],roof,roofs)
lathe('Small onion finial',[(.20,14.53),(.25,14.72),(.43,14.99),(.49,15.22),(.42,15.43),(.26,15.65),(.10,15.93),(.045,16.12)],gold,roofs)
box('Cross vertical',(0,0,16.50),(.075,.075,.85),gold,roofs)
box('Cross main bar',(0,0,16.65),(.53,.075,.075),gold,roofs)
box('Cross upper bar',(0,0,16.86),(.25,.075,.055),gold,roofs)
# eastern apse narrow window on centerline
face=('Apse',(0,D/2+2.95),(1,0),(0,1),1); window(face,0,3.1,.44,1.25)
# Presentation camera, light and neutral ground; excluded from asset collection
box('Display ground',(0,0,-.12),(35,35,.18),mat('Neutral ground',(.24,.26,.24)),stage)
def move(o,c):
    for old in list(o.users_collection): old.objects.unlink(o)
    c.objects.link(o)
bpy.ops.object.camera_add(location=(-23,-29,19)); cam=bpy.context.object; move(cam,stage); cam.rotation_euler=(Vector((0,0,7.8))-cam.location).to_track_quat('-Z','Y').to_euler(); cam.data.type='ORTHO'; cam.data.ortho_scale=22; scene.camera=cam
for name,loc,power,size in [('Key',(-12,-15,24),2500,12),('Fill',(12,-4,16),1300,10),('Rim',(0,15,22),1800,9)]:
    bpy.ops.object.light_add(type='AREA',location=loc); o=bpy.context.object; move(o,stage); o.name=name; o.data.energy=power; o.data.shape='DISK'; o.data.size=size; o.rotation_euler=(Vector((0,0,7))-o.location).to_track_quat('-Z','Y').to_euler()
scene.world.color=(.35,.35,.35); scene.render.resolution_x=1200; scene.render.resolution_y=1200; scene.render.resolution_percentage=100
# Select only architecture on opening; stage can be hidden/deleted for export.
for o in bpy.context.selected_objects: o.select_set(False)
for o in walls.objects: o.select_set(True)
bpy.context.view_layer.objects.active=walls.objects[0]
for a in bpy.context.screen.areas if bpy.context.screen else []:
    if a.type=='VIEW_3D':
        a.spaces.active.region_3d.view_distance=26; a.spaces.active.region_3d.view_location=(0,0,7); a.spaces.active.shading.color_type='MATERIAL'
scene['Historical basis']='PDF pages 3, 5–8: Martynov, Strukov, Gornostaev survey, Antipov plan. Approximate exterior, not surveyed dimensions.'
scene['Limitations']='Solid exterior massing, surface recess representations, assumed north/east details; later roof silhouette; no interior.'
# Save next to script when launched as file; otherwise home folder.
base=os.path.dirname(os.path.abspath(__file__)) if '__file__' in globals() and os.path.isfile(__file__) else os.path.expanduser('~')
if bpy.data.texts.get('create_lazar.py') and bpy.data.texts['create_lazar.py'].filepath:
    base=os.path.dirname(bpy.path.abspath(bpy.data.texts['create_lazar.py'].filepath))
path=os.path.join(base,'Lazarus_1461.blend'); bpy.ops.wm.save_as_mainfile(filepath=path); print('Saved:',path)
