#!/usr/bin/env python3
"""Assemble the self-contained dashboard: template + sample data + the codec.

One file, no bundler, no node_modules. It opens from a USB stick on a laptop
with no network, which is what you want at an evaluator's table.
"""
import json, pathlib, re

here = pathlib.Path(__file__).parent
tpl = (here / 'dashboard.template.html').read_text(encoding='utf-8')
codec = re.sub(r'^export (const|function|async function) ', r'\1 ',
               (here / 'src/codec/codec.js').read_text(encoding='utf-8'), flags=re.M)
data = json.dumps(json.loads((here / 'sample-data.json').read_text(encoding='utf-8')),
                  ensure_ascii=False, separators=(',', ':'))

out = tpl.replace('__DATA__', data).replace('__CODEC__', codec)
(here / 'dist').mkdir(exist_ok=True)
(here / 'dist/index.html').write_text(out, encoding='utf-8')
print(f'web/dist/index.html — {len(out):,} bytes, no dependencies')
