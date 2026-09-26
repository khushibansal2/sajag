"""Cut Jharkhand's districts out of the DataMeet Census 2011 district map,
simplify them and write jharkhand.json.

    python3 convert.py <path to 2011_Dist, without extension> [epsilon]

The app uses epsilon 0.002 (about 2 px on a 1000 px wide map).
"""
import math, json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from shp import read_dbf, read_shp

if len(sys.argv) < 2:
    sys.exit(__doc__)
BASE = sys.argv[1]
HERE = os.path.dirname(os.path.abspath(__file__))

NAMES = {
 'Bokaro': ('BOKARO', 'Bokaro', 'बोकारो'),
 'Chatra': ('CHATRA', 'Chatra', 'चतरा'),
 'Deoghar': ('DEOGHAR', 'Deoghar', 'देवघर'),
 'Dhanbad': ('DHANBAD', 'Dhanbad', 'धनबाद'),
 'Dumka': ('DUMKA', 'Dumka', 'दुमका'),
 'Garhwa': ('GARHWA', 'Garhwa', 'गढ़वा'),
 'Giridih': ('GIRIDIH', 'Giridih', 'गिरिडीह'),
 'Godda': ('GODDA', 'Godda', 'गोड्डा'),
 'Gumla': ('GUMLA', 'Gumla', 'गुमला'),
 'Hazaribagh': ('HAZARIBAGH', 'Hazaribagh', 'हज़ारीबाग'),
 'Jamtara': ('JAMTARA', 'Jamtara', 'जामताड़ा'),
 'Khunti': ('KHUNTI', 'Khunti', 'खूंटी'),
 'Kodarma': ('KODERMA', 'Koderma', 'कोडरमा'),
 'Latehar': ('LATEHAR', 'Latehar', 'लातेहार'),
 'Lohardaga': ('LOHARDAGA', 'Lohardaga', 'लोहरदगा'),
 'Pakur': ('PAKUR', 'Pakur', 'पाकुड़'),
 'Palamu': ('PALAMU', 'Palamu', 'पलामू'),
 'Pashchimi Singhbhum': ('WEST_SINGHBHUM', 'West Singhbhum', 'पश्चिमी सिंहभूम'),
 'Purbi Singhbhum': ('EAST_SINGHBHUM', 'East Singhbhum', 'पूर्वी सिंहभूम'),
 'Ramgarh': ('RAMGARH', 'Ramgarh', 'रामगढ़'),
 'Ranchi': ('RANCHI', 'Ranchi', 'राँची'),
 'Sahibganj': ('SAHIBGANJ', 'Sahibganj', 'साहिबगंज'),
 'Saraikela-kharsawan': ('SERAIKELA_KHARSAWAN', 'Seraikela Kharsawan', 'सरायकेला खरसावाँ'),
 'Simdega': ('SIMDEGA', 'Simdega', 'सिमडेगा'),
}

def dp(points, eps):
    if len(points) < 3: return points
    # iterative Douglas-Peucker
    keep = [False]*len(points); keep[0] = keep[-1] = True
    stack = [(0, len(points)-1)]
    while stack:
        a, b = stack.pop()
        ax, ay = points[a]; bx, by = points[b]
        dx, dy = bx-ax, by-ay
        norm = math.hypot(dx, dy)
        best, idx = -1, -1
        for i in range(a+1, b):
            px, py = points[i]
            if norm == 0: d = math.hypot(px-ax, py-ay)
            else: d = abs(dy*px - dx*py + bx*ay - by*ax)/norm
            if d > best: best, idx = d, i
        if best > eps:
            keep[idx] = True
            stack.append((a, idx)); stack.append((idx, b))
    return [p for p, k in zip(points, keep) if k]

def ring_area(r):
    s = 0
    for i in range(len(r)):
        x1, y1 = r[i]; x2, y2 = r[(i+1) % len(r)]
        s += x1*y2 - x2*y1
    return s/2

def inside(pt, ring):
    x, y = pt; c = False
    j = len(ring)-1
    for i in range(len(ring)):
        xi, yi = ring[i]; xj, yj = ring[j]
        if (yi > y) != (yj > y) and x < (xj-xi)*(y-yi)/(yj-yi)+xi: c = not c
        j = i
    return c

def seg_dist(p, a, b):
    px, py = p; ax, ay = a; bx, by = b
    dx, dy = bx-ax, by-ay
    L = dx*dx+dy*dy
    t = 0 if L == 0 else max(0, min(1, ((px-ax)*dx+(py-ay)*dy)/L))
    return math.hypot(px-(ax+t*dx), py-(ay+t*dy))

def label_point(ring):
    xs = [p[0] for p in ring]; ys = [p[1] for p in ring]
    best, bp = -1, None
    n = 60
    for i in range(n+1):
        for j in range(n+1):
            p = (min(xs)+(max(xs)-min(xs))*i/n, min(ys)+(max(ys)-min(ys))*j/n)
            if not inside(p, ring): continue
            d = min(seg_dist(p, ring[k], ring[(k+1) % len(ring)]) for k in range(len(ring)))
            if d > best: best, bp = d, p
    return bp

fields, rows = read_dbf(BASE + '.dbf')
shapes = read_shp(BASE + '.shp')
dists = [(r, s) for r, s in zip(rows, shapes) if r['ST_NM'] == 'Jharkhand']
allpts = [p for _, s in dists for ring in s for p in ring]
lon0 = min(p[0] for p in allpts); lon1 = max(p[0] for p in allpts)
lat0 = min(p[1] for p in allpts); lat1 = max(p[1] for p in allpts)
latmid = (lat0+lat1)/2
k = math.cos(math.radians(latmid))
W = (lon1-lon0)*k; H = (lat1-lat0)
print('bbox', lon0, lon1, lat0, lat1, 'aspect W/H', W/H, file=sys.stderr)
scale = max(W, H)
def proj(p):
    return ((p[0]-lon0)*k/scale, (lat1-p[1])/scale)

EPS = float(sys.argv[2]) if len(sys.argv) > 2 else 0.002
out = []
total = 0
for r, s in dists:
    rid, en, hi = NAMES[r['DISTRICT']]
    rings = []
    for ring in s:
        pr = [proj(p) for p in ring]
        if pr[0] == pr[-1]: pr = pr[:-1]
        area = abs(ring_area(pr))
        if area < 1e-5:  # drop specks
            continue
        simp = dp(pr + [pr[0]], EPS)[:-1]
        if len(simp) >= 3: rings.append(simp)
    rings.sort(key=lambda rr: -abs(ring_area(rr)))
    lab = label_point(rings[0])
    total += sum(len(x) for x in rings)
    out.append(dict(id=rid, en=en, hi=hi, census=int(r['DT_CEN_CD']), rings=rings, label=lab))
print('points', total, 'districts', len(out), file=sys.stderr)
json.dump(dict(width=W/scale, height=H/scale, lon0=lon0, lon1=lon1, lat0=lat0, lat1=lat1, districts=out), open(os.path.join(HERE, 'jharkhand.json'), 'w'))
