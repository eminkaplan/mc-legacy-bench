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
import urllib.request
import zipfile

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"


def fetch(url):
    with urllib.request.urlopen(url) as r:
        return r.read()


def get(url, path, sha1):
    if os.path.exists(path) and hashlib.sha1(open(path, "rb").read()).hexdigest() == sha1:
        return path
    data = fetch(url)
    if hashlib.sha1(data).hexdigest() != sha1:
        sys.exit(f"SHA1 mismatch: {url}")
    with open(path, "wb") as f:
        f.write(data)
    return path


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
    a = meta["assetIndex"]
    get(a["url"], os.path.join(dest, "assets", "indexes", f"{a['id']}.json"), a["sha1"])

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
    print(f"{version}: client, {len(os.listdir(os.path.join(dest, 'libs')))} libraries, natives -> {dest}")


if __name__ == "__main__":
    main()
