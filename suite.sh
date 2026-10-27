#!/bin/bash
# The standard protocol: every version x Java pair, back to back, so runs taken minutes apart are compared.
#   GPUS="0 1" ./suite.sh            # DRI_PRIME values to test; "0" alone on a single-GPU machine (default)
#   REPEAT=2 ./suite.sh              # repeat the whole set (recommended when you have the time)
#   VERSIONS="1.5.2 1.8.9" ./suite.sh  # a subset (default: every version in versions/)
# Each run takes about 2 minutes. Results land in results/raw/.
set -uo pipefail
cd "$(dirname "$0")"
GPUS=${GPUS:-0}; REPEAT=${REPEAT:-1}; SECS=${SECS:-90}; VIEW=${VIEW:-normal}
VERSIONS=${VERSIONS:-$(ls versions | python3 -I tools/vsort.py | tr '\n' ' ')}
for r in $(seq 1 "$REPEAT"); do
    for g in $GPUS; do
        for v in $VERSIONS; do
            JAVAS=8; VARIANTS=default
            # shellcheck source=/dev/null
            source "versions/$v/version.env"
            for j in $JAVAS; do
                for var in $VARIANTS; do
                    ./run.sh "$v" "suite-gpu$g-j$j-$var-r$r" "$g" "$j" "$SECS" "$VIEW" "$var"
                done
            done
        done
    done
done
echo "suite done: python3 tools/export.py <machine>-<cpu>-<gpu>"
