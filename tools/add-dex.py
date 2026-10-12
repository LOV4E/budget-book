# -*- coding: utf-8 -*-
"""Append classes.dex to an aapt2-linked apk without re-compressing the resources.

The dex entry must be stored uncompressed at the archive root; resources.arsc is
written first by aapt2 and left untouched.
"""
import sys
import zipfile

apk_path = sys.argv[1]
dex_path = sys.argv[2]

with open(dex_path, "rb") as fh:
    dex_bytes = fh.read()

with zipfile.ZipFile(apk_path, "a", zipfile.ZIP_DEFLATED) as zf:
    names = set(zf.namelist())
    if "classes.dex" in names:
        raise SystemExit("classes.dex already present in apk")
    info = zipfile.ZipInfo("classes.dex")
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o644 << 16
    zf.writestr(info, dex_bytes)

with zipfile.ZipFile(apk_path) as zf:
    print("entries:", ", ".join(zf.namelist()))
    print("classes.dex bytes:", zf.getinfo("classes.dex").file_size)
