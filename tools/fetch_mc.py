#!/usr/bin/env python3
"""Download an official Minecraft client (+ libraries, Linux natives, asset index) from Mojang's servers.

Every file is checked against the SHA1 in Mojang's version manifest. Nothing here is redistributed:
the files are fetched on the user's machine, and the user must own Minecraft (Mojang EULA).

usage: fetch_mc.py <version> <dest dir>
"""
import hashlib
import json
import os
import sys
import time
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
RESOURCES = "https://resources.download.minecraft.net"


def fetch(url, tries=5):
    for i in range(tries):  # transient DNS / connection failures are common on long asset downloads
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                return r.read()
        except OSError as e:
            if i == tries - 1:
                raise
            print(f"retry {i + 1}/{tries - 1} {url}: {e}", file=sys.stderr)
            time.sleep(2 ** i)


def get(url, path, sha1):
    if os.path.exists(path) and hashlib.sha1(open(path, "rb").read()).hexdigest() == sha1:
        return path
    data = fetch(url)
    if hashlib.sha1(data).hexdigest() != sha1:
        sys.exit(f"SHA1 mismatch: {url}")
    with open(path, "wb") as f:
        f.write(data)
    return path


def fetch_assets(index_path, dest):
    """Every asset object except sounds and music (.ogg): textures, fonts, languages, shaders. 26.x crashes
    without them; older versions only log warnings, but all get the same files for a like-for-like run."""
    objects = json.load(open(index_path))["objects"]
    todo = [o["hash"] for k, o in objects.items() if not k.endswith(".ogg")]
    root = os.path.join(dest, "assets", "objects")

    def one(h):
        os.makedirs(os.path.join(root, h[:2]), exist_ok=True)
        get(f"{RESOURCES}/{h[:2]}/{h}", os.path.join(root, h[:2], h), h)

    with ThreadPoolExecutor(16) as pool:
        list(pool.map(one, todo))
    return len(todo)


def allowed(lib):
    rules = lib.get("rules")
    if not rules:
        return True
    ok = False
    for r in rules:
        if "os" not in r or r["os"].get("name") == "linux":
            ok = r["action"] == "allow"
    return ok


def main():
    version, dest = sys.argv[1], sys.argv[2]
    os.makedirs(os.path.join(dest, "libs"), exist_ok=True)
    os.makedirs(os.path.join(dest, "natives"), exist_ok=True)
    os.makedirs(os.path.join(dest, "assets", "indexes"), exist_ok=True)

    url = next(v["url"] for v in json.loads(fetch(MANIFEST))["versions"] if v["id"] == version)
    meta = json.loads(fetch(url))
    with open(os.path.join(dest, f"{version}.json"), "w") as f:
        json.dump(meta, f)

    c = meta["downloads"]["client"]
    get(c["url"], os.path.join(dest, "client.jar"), c["sha1"])
    if "client_mappings" in meta["downloads"]:  # official name mappings, 1.14.4 and later (reference only)
        m = meta["downloads"]["client_mappings"]
        get(m["url"], os.path.join(dest, "client_mappings.txt"), m["sha1"])
    a = meta["assetIndex"]
    index = get(a["url"], os.path.join(dest, "assets", "indexes", f"{a['id']}.json"), a["sha1"])
    assets = 0 if a["id"] in ("legacy", "pre-1.6") else fetch_assets(index, dest)

    for lib in meta["libraries"]:
        if not allowed(lib):
            continue
        d = lib["downloads"]
        if "artifact" in d:
            x = d["artifact"]
            get(x["url"], os.path.join(dest, "libs", os.path.basename(x["path"])), x["sha1"])
        if "natives" in lib and "linux" in lib["natives"]:
            x = d["classifiers"][lib["natives"]["linux"].replace("${arch}", "64")]
            p = get(x["url"], os.path.join(dest, "natives", "_" + os.path.basename(x["path"])), x["sha1"])
            with zipfile.ZipFile(p) as z:
                for n in z.namelist():
                    if not n.startswith("META-INF") and not n.endswith("/"):
                        z.extract(n, os.path.join(dest, "natives"))
            os.remove(p)
    print(f"{version}: client, {len(os.listdir(os.path.join(dest, 'libs')))} libraries, natives, {assets} asset objects -> {dest}")


if __name__ == "__main__":
    main()
