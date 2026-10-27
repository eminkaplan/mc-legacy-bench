#!/bin/bash
# One benchmark run: start the client, create a fresh singleplayer world, sample FPS once a second.
#   usage: run.sh <version> <label> <DRI_PRIME 0|1> <java> [seconds=90] [view=normal|far] [variant=default]
#   variant: a value from the version's VARIANTS (e.g. the graphics backend of 26.x)
#   output: results/raw/<version>-<label>.log (harness + game log), .meta (run info), .sys (CPU MHz, AMD GPU busy %)
# Hands off the keyboard and mouse while it runs: the harness locks input, but focus changes still add noise.
set -uo pipefail
ROOT=$(cd "$(dirname "$0")" && pwd)
VER=$1; LABEL=$2; PRIME=$3; JV=$4; SECS=${5:-90}; VIEW=${6:-normal}; VARIANT=${7:-default}
[ -f "$ROOT/versions/$VER/version.env" ] || { echo "unknown version $VER (see versions/)"; exit 1; }
JAWT_SHIM=0; UNSIGN=0; JVM_FLAGS=""; WINDOW_API=glfw; RUN_ENV=""
# shellcheck source=/dev/null
source "$ROOT/versions/$VER/version.env"
JVM_FLAGS="$JVM_FLAGS ${BENCH_JVM_FLAGS:-}"   # extra JVM flags for debugging, e.g. LWJGL loader output
V=$ROOT/work/mc-$VER
JH=$ROOT/work/jdk$JV
[ -x "$JH/bin/java" ] && { [ -f "$V/build/Bench.class" ] || [ -f "$V/build/bench/Bench.class" ]; } || { echo "run ./setup.sh $VER first"; exit 1; }
NAT=$V/natives; [ "$JAWT_SHIM" = 1 ] && [ "$JV" != 8 ] && NAT=$V/natives-shim
CLIENT=$V/client.jar; [ "$UNSIGN" = 1 ] && CLIENT=$V/client-unsigned.jar
CP=$V/build:$CLIENT:$(ls "$V"/libs/*.jar | tr '\n' ':')
G=$ROOT/work/game-$VER-$LABEL; rm -rf "$G"; mkdir -p "$G" "$ROOT/results/raw"
OUT=$ROOT/results/raw/$VER-$LABEL
bench_options "$VIEW" "$VARIANT" > "$G/options.txt"
read -r -a ARGS <<< "$(bench_args)"

{
    echo "version=$VER"; echo "label=$LABEL"; echo "prime=$PRIME"; echo "java=$JV"; echo "seconds=$SECS"; echo "view=$VIEW"; echo "variant=$VARIANT"
    echo "gl_renderer=$(DRI_PRIME=$PRIME glxinfo -B 2>/dev/null | sed -n 's/.*OpenGL renderer string: //p')"
    echo "java_version=$("$JH/bin/java" -version 2>&1 | head -1)"
    echo "started=$(date -u +%FT%TZ)"
} > "$OUT.meta"

( while sleep 1; do
    echo "$(date +%s) mhz=$(awk -F: '/MHz/{s+=$2;n++}END{printf "%d",s/n}' /proc/cpuinfo) amdbusy=$(cat /sys/class/drm/card*/device/gpu_busy_percent 2>/dev/null | head -1)"
  done > "$OUT.sys" ) &
SAMPLER=$!

cd "$G"
# A client that crashes or hangs is killed after the run length + 8 minutes.
# MESA_VK_DEVICE_SELECT_FORCE_DEFAULT_DEVICE makes Vulkan clients follow DRI_PRIME too (they pick the discrete GPU otherwise).
# The locale is pinned so the harness logs decimal points, not the system's decimal comma.
# shellcheck disable=SC2086
timeout --signal=KILL $((SECS + 480)) env $RUN_ENV LD_LIBRARY_PATH=$NAT DRI_PRIME=$PRIME MESA_VK_DEVICE_SELECT_FORCE_DEFAULT_DEVICE=1 vblank_mode=0 "$JH/bin/java" -Xms512M -Xmx${XMX:-2G} -Duser.language=en -Duser.country=US $JVM_FLAGS \
    -Djava.library.path="$NAT" -Dbench.gamedir="$G" -Dbench.seconds="$SECS" -Dbench.seed=1234567890 \
    -Dbench.windowlock="bench.$([ "$WINDOW_API" = sdl ] && echo SdlLock || echo GlfwLock)" \
    -Dbench.mappings="$([ -f "$V/client_mappings.txt" ] && echo "$V/client_mappings.txt")" \
    -cp "$CP" "${ARGS[@]}" > "$OUT.log" 2>&1
rc=$?
kill $SAMPLER
# Clients that report their graphics backend/device (26.x) say which one actually ran.
sed -n 's/.*Using graphics backend \([A-Za-z]*\).*/backend=\1/p; s/.*Using graphics device: \(.*\)/device=\1/p' "$OUT.log" | head -2 >> "$OUT.meta"
echo "$VER $LABEL exit=$rc -> $OUT.log"
exit $rc
