#!/usr/bin/env python3
"""Summarise benchmark logs written by run.sh.

usage: analyze.py results/raw/1.5.2-j8-amd.log [...]
Prints, per run: time to title screen, world creation, FPS (first 10 s / rest / last 30 s), chunk updates,
when chunk updates settle, AMD GPU busy %, total CPU cores used and the per-thread CPU breakdown.
"""
import os
import re
import statistics as st
import sys


def num(x):
    return float(x.replace(",", "."))


def analyze(path):
    t = open(path).read()
    title = num(re.search(r"\[BENCH\s+([\d,.]+)\] TITLE_SCREEN", t).group(1))
    create = num(re.search(r"CREATE_WORLD returned after ([\d,.]+)", t).group(1))
    s = re.findall(r'SAMPLE fps=(\d+) debug="\d+ fps, (-?\d+) chunk updates".*?heapMB=(\d+) win=(\S+)', t)
    fps = [int(a) for a, _, _, _ in s]
    cu = [int(b) for _, b, _, _ in s]
    heap = max(int(h) for _, _, h, _ in s)
    settled = next((i for i in range(3, len(cu) - 5) if all(c < 5 for c in cu[i:i + 5])), None)
    busy = None
    sysf = path[:-4] + ".sys"
    if os.path.exists(sysf):
        b = [int(m) for m in re.findall(r"amdbusy=(\d+)", open(sysf).read())]
        busy = st.mean(b) if b else None
    thr = re.search(r"THREADS total=([\d,.]+) cores.*?\n((?:  .*\n)+)", t)
    return dict(run=os.path.basename(path)[:-4], title=title, create=create, first10=st.mean(fps[:10]),
                avg=st.mean(fps[10:]), last30=st.mean(fps[-30:]), p5=sorted(fps[10:])[len(fps[10:]) // 20],
                chunk_updates=st.mean(cu[10:]), settled=settled, heap_mb=heap, amd_busy=busy,
                cores=num(thr.group(1)) if thr else None, threads=thr.group(2).rstrip() if thr else "")


def main():
    for p in sys.argv[1:]:
        r = analyze(p)
        busy = "-" if r["amd_busy"] is None else f"{r['amd_busy']:.0f}%"
        settled = "never" if r["settled"] is None else f"{r['settled']}s"
        print(f"{r['run']:22s} title={r['title']:.1f}s create={r['create']:.1f}s fps first10={r['first10']:.0f} "
              f"avg={r['avg']:.0f} last30={r['last30']:.0f} p5={r['p5']} chunkupd/s={r['chunk_updates']:.0f} "
              f"settled={settled} heap={r['heap_mb']}MB amdBusy={busy} cores={r['cores']}")
        print(r["threads"])


if __name__ == "__main__":
    main()
