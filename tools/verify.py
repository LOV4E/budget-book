# -*- coding: utf-8 -*-
"""Static cross-checks between the layouts, the Java sources and the built dex.

Catches the realistic runtime crashes that a compile cannot see:
  * findViewById(R.id.X) where X lives in a different layout  -> NPE
  * (SomeView) findViewById(R.id.X) where the tag is different -> ClassCastException
  * an activity class missing from classes.dex
"""
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

ROOT = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\budget-book"
APP = os.path.join(ROOT, "app")
ANDROID = "{http://schemas.android.com/apk/res/android}"

fails = []


def ok(msg):
    print("ok    " + msg)


def bad(msg):
    fails.append(msg)
    print("FAIL  " + msg)


def layout_ids(path):
    """id name -> view tag, for every @+id in a layout."""
    root = ET.parse(path).getroot()
    out = {}
    for el in root.iter():
        aid = el.get(ANDROID + "id")
        if aid and aid.startswith("@+id/"):
            out[aid[5:]] = el.tag
    return out


LAYOUTS = {
    "activity_main": layout_ids(os.path.join(APP, "res", "layout", "activity_main.xml")),
    "item_txn": layout_ids(os.path.join(APP, "res", "layout", "item_txn.xml")),
    "activity_add": layout_ids(os.path.join(APP, "res", "layout", "activity_add.xml")),
    "dialog_budget": layout_ids(os.path.join(APP, "res", "layout", "dialog_budget.xml")),
}
for name, ids in LAYOUTS.items():
    print("      layout %-14s ids=%d" % (name, len(ids)))

# Which layouts each java file is allowed to resolve ids from.
SOURCES = {
    "MainActivity.java": ["activity_main", "item_txn", "dialog_budget"],
    "AddActivity.java": ["activity_add"],
}

# Casts that are legal even when the tag is a subclass.
COMPATIBLE = {
    "View": {"View", "LinearLayout", "FrameLayout", "TextView", "EditText",
             "ProgressBar", "ListView", "ScrollView", "HorizontalScrollView", "Space"},
}

CAST_RE = re.compile(r"\(\s*([A-Za-z0-9_.]+)\s*\)\s*findViewById\(R\.id\.([A-Za-z0-9_]+)\)")
PLAIN_RE = re.compile(r"(?<!\)\s)findViewById\(R\.id\.([A-Za-z0-9_]+)\)")

for java, allowed in SOURCES.items():
    path = os.path.join(APP, "src", "com", "dsh", "budgetbook", java)
    text = open(path, encoding="utf-8").read()

    pool = {}
    for lay in allowed:
        pool.update(LAYOUTS[lay])

    seen = set()
    for m in CAST_RE.finditer(text):
        cast, rid = m.group(1), m.group(2)
        simple = cast.split(".")[-1]
        seen.add(rid)
        if rid not in pool:
            bad("%s: id/%s not found in %s" % (java, rid, allowed))
            continue
        tag = pool[rid]
        if simple == tag or simple in COMPATIBLE and tag in COMPATIBLE[simple]:
            ok("%s: (%s) id/%s matches <%s>" % (java, simple, rid, tag))
        else:
            bad("%s: casts id/%s to %s but the layout tag is <%s>" % (java, rid, simple, tag))

    for m in PLAIN_RE.finditer(text):
        rid = m.group(1)
        if rid in seen:
            continue
        if rid not in pool:
            bad("%s: id/%s not found in %s" % (java, rid, allowed))
        else:
            ok("%s: id/%s matches <%s>" % (java, rid, pool[rid]))

# ---- every id used anywhere in java must exist in some layout ----
all_ids = set()
for ids in LAYOUTS.values():
    all_ids.update(ids.keys())

used = set()
for java in SOURCES:
    path = os.path.join(APP, "src", "com", "dsh", "budgetbook", java)
    text = open(path, encoding="utf-8").read()
    used.update(re.findall(r"R\.id\.([A-Za-z0-9_]+)", text))
missing = sorted(used - all_ids)
if missing:
    bad("R.id references with no layout definition: %s" % missing)
else:
    ok("all %d R.id references resolve to a layout id" % len(used))

unused = sorted(all_ids - used)
print("      layout ids not referenced from java (fine): %s" % unused)

# ---- classes present in the built dex ----
apk = os.path.join(ROOT, "JianZhang-1.0.apk")
with zipfile.ZipFile(apk) as zf:
    dex = zf.read("classes.dex")
    names = zf.namelist()

for cls in ("MainActivity", "AddActivity", "Store", "Txn", "Money"):
    desc = ("Lcom/dsh/budgetbook/%s;" % cls).encode("ascii")
    (ok if desc in dex else bad)("classes.dex contains %s" % cls)

for entry in ("AndroidManifest.xml", "resources.arsc", "classes.dex"):
    (ok if entry in names else bad)("apk contains %s" % entry)

dex_entries = [n for n in names if n.startswith("classes") and n.endswith(".dex")]
if dex_entries == ["classes.dex"]:
    ok("apk has exactly one dex: %s" % dex_entries)
else:
    bad("unexpected dex entries: %s" % dex_entries)

print("")
print("STATIC-OK" if not fails else "STATIC-FAILED (%d)" % len(fails))
sys.exit(1 if fails else 0)
