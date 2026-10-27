#!/usr/bin/env python3
"""Sort Minecraft version ids from stdin in release order: b1.7.3 < 1.0 < 1.2.5 < ... < 1.21.11 < 26.1."""
import re
import sys


def key(v):
    stage = 0 if v.startswith("a") else 1 if v.startswith("b") else 2
    return (stage, [int(x) for x in re.findall(r"\d+", v)])


if __name__ == "__main__":
    for v in sorted((l.strip() for l in sys.stdin if l.strip()), key=key):
        print(v)
