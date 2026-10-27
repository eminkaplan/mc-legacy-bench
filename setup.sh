#!/bin/bash
# Prepares everything under ./work: the Temurin JDKs the versions need, the official clients, the libjawt shim
# for LWJGL 2 on Java 9+, and the compiled benchmark harnesses. Re-running skips what is already there.
#   ./setup.sh               all versions in versions/
#   ./setup.sh 1.5.2 1.8.9   only these
# Needs: bash, curl, python3, gcc, sha256sum.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")" && pwd)
WORK=$ROOT/work
mkdir -p "$WORK"
VERSIONS=("$@")
[ ${#VERSIONS[@]} -gt 0 ] || mapfile -t VERSIONS < <(ls "$ROOT/versions" | python3 -I "$ROOT/tools/vsort.py")

jdk() { # <major>
    local dir=$WORK/jdk$1
    [ -x "$dir/bin/java" ] && return
    read -r url sum < <(curl -s "https://api.adoptium.net/v3/assets/latest/$1/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse" |
        python3 -I -c 'import json,sys; b=json.load(sys.stdin)[0]["binary"]["package"]; print(b["link"], b["checksum"])')
    echo "Temurin $1: $url"
    curl -sL -o "$WORK/jdk.tar.gz" "$url"
    echo "$sum  $WORK/jdk.tar.gz" | sha256sum -c -
    mkdir -p "$dir" && tar -xzf "$WORK/jdk.tar.gz" -C "$dir" --strip-components=1 && rm "$WORK/jdk.tar.gz"
}

# Java 9+ has no SUNWprivate_1.1 symbol version in libjawt.so, which LWJGL 2's natives link against.
# natives-shim = copies whose libjawt.so dependency is renamed to libjawx.so, a shim that forwards to the real one.
build_shim() {
    [ -f "$WORK/shim/libjawx.so" ] && return
    mkdir -p "$WORK/shim"
    gcc -O2 -shared -fPIC -Wl,--version-script="$ROOT/shim/jawx.map" -Wl,-soname,libjawx.so \
        -o "$WORK/shim/libjawx.so" "$ROOT/shim/jawx.c" -ldl
}

jdk 25   # its javac compiles every harness (--release per version)
for ver in "${VERSIONS[@]}"; do
    (
        JAVAS=8; JAWT_SHIM=0; UNSIGN=0; JAVAC_RELEASE=8; MODERN=0; WINDOW_API=glfw
        # shellcheck source=/dev/null
        source "$ROOT/versions/$ver/version.env"
        V=$WORK/mc-$ver
        for j in $JAVAS; do jdk "$j"; done
        python3 -I "$ROOT/tools/fetch_mc.py" "$ver" "$V"

        CLIENT=$V/client.jar
        if [ "$UNSIGN" = 1 ]; then
            # The harness lives in the client's default package, so a signed jar refuses it: make an unsigned copy.
            python3 -I - "$V/client.jar" "$V/client-unsigned.jar" <<'EOF'
import sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zo:
    for item in zi.infolist():
        n = item.filename
        if n.startswith("META-INF/") and (n.endswith((".SF", ".RSA", ".DSA", ".EC")) or n == "META-INF/MANIFEST.MF"):
            continue
        zo.writestr(item, zi.read(n))
EOF
            CLIENT=$V/client-unsigned.jar
        fi

        if [ "$JAWT_SHIM" = 1 ]; then
            build_shim
            rm -rf "$V/natives-shim" && cp -r "$V/natives" "$V/natives-shim" && cp "$WORK/shim/libjawx.so" "$V/natives-shim/"
            python3 -I - "$V/natives-shim" <<'EOF'
import glob, os, sys
for p in glob.glob(os.path.join(sys.argv[1], "liblwjgl*.so")):
    b = open(p, "rb").read()
    open(p, "wb").write(b.replace(b"\0libjawt.so\0", b"\0libjawx.so\0"))
EOF
        fi

        rm -rf "$V/build"
        "$WORK/jdk25/bin/javac" --release "$JAVAC_RELEASE" -nowarn -Xlint:-options \
            -cp "$CLIENT:$(ls "$V"/libs/*.jar | tr '\n' ':')" -d "$V/build" \
            "$ROOT"/versions/"$ver"/*.java "$ROOT"/common/bench/*.java $([ "$MODERN" = 1 ] && echo "$ROOT"/common/modern/*.java "$ROOT"/common/"$WINDOW_API"/*.java) \
            2>&1 | grep -vE '^(warning|Note):' || true
        [ -f "$V/build/Bench.class" ] || [ -f "$V/build/bench/Bench.class" ] || { echo "$ver: harness did not compile"; exit 1; }
        echo "$ver ready (Java $JAVAS)"
    )
done
echo "setup done: GPUS=\"0\" ./suite.sh"
