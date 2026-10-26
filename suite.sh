#!/bin/bash
# The standard protocol: every version x Java pair, back to back, so runs taken minutes apart are compared.
#   GPUS="0 1" ./suite.sh     # DRI_PRIME values to test; "0" alone on a single-GPU machine (default)
#   REPEAT=2 ./suite.sh       # repeat the whole set (recommended when you have the time)
# Each run takes about 2 minutes: 4 runs per GPU per repeat. Results land in results/raw/.
set -uo pipefail
cd "$(dirname "$0")"
GPUS=${GPUS:-0}; REPEAT=${REPEAT:-1}; SECS=${SECS:-90}; VIEW=${VIEW:-normal}
[ -d work/mc189/build ] || { echo "run ./setup.sh first"; exit 1; }
for r in $(seq 1 "$REPEAT"); do
    for g in $GPUS; do
        for v in 1.5.2 1.8.9; do
            for j in 8 21; do
                ./run.sh "$v" "suite-gpu$g-j$j-r$r" "$g" "$j" "$SECS" "$VIEW"
            done
        done
    done
done
echo "suite done: python3 tools/export.py <machine>-<cpu>-<gpu>"
