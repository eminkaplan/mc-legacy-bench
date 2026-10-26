#!/bin/bash
# One benchmark run: start the client, create a fresh singleplayer world, sample FPS once a second.
#   usage: run.sh <1.5.2|1.8.9> <label> <DRI_PRIME 0|1> <java 8|21> [seconds=90] [view=normal|far]
#   output: results/raw/<version>-<label>.log (harness + game log) and .sys (CPU MHz, AMD GPU busy %)
# Hands off the keyboard and mouse while it runs: the harness locks input, but focus changes still add noise.
set -uo pipefail
ROOT=$(cd "$(dirname "$0")" && pwd)
VER=$1; LABEL=$2; PRIME=$3; JV=$4; SECS=${5:-90}; VIEW=${6:-normal}
JH=$ROOT/work/jdk$JV
case $VER in 1.5.2) V=$ROOT/work/mc152 ;; 1.8.9) V=$ROOT/work/mc189 ;; *) echo "unknown version $VER"; exit 1 ;; esac
NAT=$V/natives; [ "$JV" = 8 ] || NAT=$V/natives-j21
G=$ROOT/work/game-$VER-$LABEL; rm -rf "$G"; mkdir -p "$G" "$ROOT/results/raw"
OUT=$ROOT/results/raw/$VER-$LABEL

if [ "$VER" = 1.5.2 ]; then
    # 1.5.2 viewDistance: 0 = Far (16 chunks), 1 = Normal (8 chunks)
    cat > "$G/options.txt" <<OPT
music:0.0
sound:0.0
viewDistance:$([ "$VIEW" = far ] && echo 0 || echo 1)
fpsLimit:0
enableVsync:false
fancyGraphics:true
pauseOnLostFocus:false
snooperEnabled:false
OPT
    CP=$V/build:$V/client.jar:$(ls "$V"/libs/*.jar | tr '\n' ':')
    ARGS=(Bench Bench -)
else
    cat > "$G/options.txt" <<OPT
renderDistance:$([ "$VIEW" = far ] && echo 16 || echo 8)
maxFps:260
enableVsync:false
fancyGraphics:true
pauseOnLostFocus:false
snooperEnabled:false
soundCategory_master:0.0
OPT
    CP=$V/build:$V/client-unsigned.jar:$(ls "$V"/libs/*.jar | tr '\n' ':')
    ARGS=(Bench --username Bench --version 1.8.9 --gameDir "$G" --assetsDir "$V/assets" --assetIndex 1.8
          --uuid 00000000000000000000000000000000 --accessToken 0 --userProperties {} --userType legacy --width 854 --height 480)
fi

{
    echo "version=$VER"; echo "label=$LABEL"; echo "prime=$PRIME"; echo "java=$JV"; echo "seconds=$SECS"; echo "view=$VIEW"
    echo "gl_renderer=$(DRI_PRIME=$PRIME glxinfo -B 2>/dev/null | sed -n 's/.*OpenGL renderer string: //p')"
    echo "java_version=$("$JH/bin/java" -version 2>&1 | head -1)"
    echo "started=$(date -u +%FT%TZ)"
} > "$OUT.meta"

( while sleep 1; do
    echo "$(date +%s) mhz=$(awk -F: '/MHz/{s+=$2;n++}END{printf "%d",s/n}' /proc/cpuinfo) amdbusy=$(cat /sys/class/drm/card*/device/gpu_busy_percent 2>/dev/null | head -1)"
  done > "$OUT.sys" ) &
SAMPLER=$!

cd "$G"
LD_LIBRARY_PATH=$NAT DRI_PRIME=$PRIME vblank_mode=0 "$JH/bin/java" -Xms512M -Xmx1G \
    -Djava.library.path="$NAT" -Dbench.gamedir="$G" -Dbench.seconds="$SECS" -Dbench.seed=1234567890 \
    -cp "$CP" "${ARGS[@]}" > "$OUT.log" 2>&1
rc=$?
kill $SAMPLER
echo "$VER $LABEL exit=$rc -> $OUT.log"
exit $rc
