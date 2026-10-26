#!/bin/bash
# Prepares everything under ./work: Temurin 8 + 21, the official 1.5.2 / 1.8.9 clients, the libjawt shim
# for Java 21, and the compiled benchmark harnesses. Re-running skips what is already there.
# Needs: bash, curl, python3, gcc, sha256sum.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")" && pwd)
WORK=$ROOT/work
mkdir -p "$WORK"

jdk() { # <major> <dir>
    local dir=$WORK/$2
    [ -x "$dir/bin/java" ] && return
    read -r url sum < <(curl -s "https://api.adoptium.net/v3/assets/latest/$1/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse" |
        python3 -I -c 'import json,sys; b=json.load(sys.stdin)[0]["binary"]["package"]; print(b["link"], b["checksum"])')
    echo "Temurin $1: $url"
    curl -sL -o "$WORK/jdk.tar.gz" "$url"
    echo "$sum  $WORK/jdk.tar.gz" | sha256sum -c -
    mkdir -p "$dir" && tar -xzf "$WORK/jdk.tar.gz" -C "$dir" --strip-components=1 && rm "$WORK/jdk.tar.gz"
}
jdk 8 jdk8
jdk 21 jdk21

python3 -I "$ROOT/tools/fetch_mc.py" 1.5.2 "$WORK/mc152"
python3 -I "$ROOT/tools/fetch_mc.py" 1.8.9 "$WORK/mc189"

# 1.8.9's client.jar is signed; the harness lives in the same (default) package, so it needs an unsigned copy.
python3 -I - "$WORK/mc189/client.jar" "$WORK/mc189/client-unsigned.jar" <<'EOF'
import sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zo:
    for item in zi.infolist():
        n = item.filename
        if n.startswith("META-INF/") and (n.endswith((".SF", ".RSA", ".DSA")) or n == "META-INF/MANIFEST.MF"):
            continue
        zo.writestr(item, zi.read(n))
EOF

# Java 9+ has no SUNWprivate_1.1 symbol version in libjawt.so, which LWJGL 2's natives link against.
# natives-j21 = copies whose libjawt.so dependency is renamed to libjawx.so, a shim that forwards to the real one.
mkdir -p "$WORK/shim"
gcc -O2 -shared -fPIC -Wl,--version-script="$ROOT/shim/jawx.map" -Wl,-soname,libjawx.so -o "$WORK/shim/libjawx.so" "$ROOT/shim/jawx.c" -ldl
for v in mc152 mc189; do
    rm -rf "$WORK/$v/natives-j21" && cp -r "$WORK/$v/natives" "$WORK/$v/natives-j21" && cp "$WORK/shim/libjawx.so" "$WORK/$v/natives-j21/"
    python3 -I - "$WORK/$v/natives-j21" <<'EOF'
import glob, os, sys
for p in glob.glob(os.path.join(sys.argv[1], "liblwjgl*.so")):
    b = open(p, "rb").read()
    open(p, "wb").write(b.replace(b"\0libjawt.so\0", b"\0libjawx.so\0"))
EOF
done

# The harnesses target Java 8 bytecode so the same classes run on both JDKs.
JAVAC=$WORK/jdk21/bin/javac
rm -rf "$WORK/mc152/build" "$WORK/mc189/build"
"$JAVAC" --release 8 -nowarn -cp "$WORK/mc152/client.jar" -d "$WORK/mc152/build" \
    "$ROOT/v1.5.2/Bench.java" "$ROOT/common/ThreadCpu.java" 2>&1 | grep -v '^warning' || true
"$JAVAC" --release 8 -nowarn -cp "$WORK/mc189/client-unsigned.jar:$(ls "$WORK"/mc189/libs/*.jar | tr '\n' ':')" -d "$WORK/mc189/build" \
    "$ROOT/v1.8.9/Bench.java" "$ROOT/common/ThreadCpu.java" 2>&1 | grep -v '^warning' || true
echo "setup done: ./run.sh 1.5.2 test 0 8 30"
