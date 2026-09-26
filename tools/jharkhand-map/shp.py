"""Minimal ESRI shapefile + dBase reader (polygons only), enough for one state."""
import struct, sys, json

def read_dbf(path):
    with open(path, 'rb') as f:
        data = f.read()
    n = struct.unpack('<I', data[4:8])[0]
    hlen, rlen = struct.unpack('<HH', data[8:12])
    fields = []
    off = 32
    while data[off] != 0x0D:
        name = data[off:off+11].split(b'\0')[0].decode('latin-1')
        ftype = chr(data[off+11]); flen = data[off+16]
        fields.append((name, ftype, flen))
        off += 32
    rows = []
    for i in range(n):
        rec = data[hlen + i*rlen: hlen + (i+1)*rlen]
        pos = 1
        row = {}
        for name, ftype, flen in fields:
            raw = rec[pos:pos+flen]; pos += flen
            try: val = raw.decode('utf-8').strip()
            except UnicodeDecodeError: val = raw.decode('latin-1').strip()
            row[name] = val
        rows.append(row)
    return fields, rows

def read_shp(path):
    with open(path, 'rb') as f:
        data = f.read()
    shapes = []
    off = 100
    while off < len(data):
        recno, clen = struct.unpack('>II', data[off:off+8])
        content = data[off+8: off+8+clen*2]
        off += 8 + clen*2
        stype = struct.unpack('<i', content[0:4])[0]
        if stype == 0:
            shapes.append([]); continue
        assert stype in (5, 15, 25), stype
        nparts, npoints = struct.unpack('<ii', content[36:44])
        parts = list(struct.unpack('<%di' % nparts, content[44:44+4*nparts]))
        pts_off = 44 + 4*nparts
        pts = [struct.unpack('<dd', content[pts_off+16*i: pts_off+16*i+16]) for i in range(npoints)]
        rings = []
        for k, start in enumerate(parts):
            end = parts[k+1] if k+1 < nparts else npoints
            rings.append(pts[start:end])
        shapes.append(rings)
    return shapes

if __name__ == '__main__':
    base = sys.argv[1]
    fields, rows = read_dbf(base + '.dbf')
    print(fields)
    shapes = read_shp(base + '.shp')
    print(len(rows), len(shapes))
    for r, s in zip(rows, shapes):
        if 'harkhand' in json.dumps(r):
            print(r, len(s), sum(len(x) for x in s))
