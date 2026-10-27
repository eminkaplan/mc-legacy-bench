#!/usr/bin/env python3
"""Turn the suite's raw logs into a results folder that can be committed.

usage: export.py <machine>-<cpu>-<gpu> [--all] [--force]
writes results/<name>/: system.md (hardware/software, collected automatically), summary.csv (one row per run),
samples.csv (one row per second), threads.txt (CPU time per thread). Raw logs stay out of git: they carry
absolute paths. --all also exports runs not started by suite.sh.
"""
import csv
import glob
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import analyze  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SUMMARY_COLS = ["version", "gpu_prime", "gpu_renderer", "java", "variant", "backend", "view", "repeat", "run", "title_s", "create_world_s",
                "fps_first10", "fps_avg_after10", "fps_last30", "fps_p5", "chunk_updates_per_s", "chunks_settled_s",
                "max_heap_mb", "amd_busy_pct", "cpu_cores"]


def sh(cmd):
    try:
        return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=20).stdout.strip()
    except Exception:
        return ""


def read(path):
    try:
        return open(path).read().strip()
    except OSError:
        return ""


def meta(path):
    m = {}
    for line in read(path).splitlines():
        k, _, v = line.partition("=")
        m[k] = v
    return m


def system_md(renderers, javas):
    cpu = re.search(r"model name\s*:\s*(.*)", read("/proc/cpuinfo"))
    mem = re.search(r"MemTotal:\s*(\d+)", read("/proc/meminfo"))
    osr = re.search(r'PRETTY_NAME="(.*)"', read("/etc/os-release"))
    rows = [
        ("Machine", f"{read('/sys/class/dmi/id/sys_vendor')} {read('/sys/class/dmi/id/product_version')} "
                    f"({read('/sys/class/dmi/id/product_name')})"),
        ("CPU", f"{cpu.group(1) if cpu else '?'}, {os.cpu_count()} threads"),
        ("RAM", f"{int(mem.group(1)) // 1024 // 1024} GB" if mem else "?"),
        ("GPUs (lspci)", "<br>".join(re.sub(r"^\S+\s+", "", l) for l in sh("lspci | grep -Ei 'vga|3d|display'").splitlines())),
        ("OpenGL renderer per DRI_PRIME", "<br>".join(f"{p}: {r}" for p, r in sorted(renderers.items()))),
        ("Mesa / GL version", sh("glxinfo -B 2>/dev/null | sed -n 's/.*OpenGL version string: //p'")),
        ("OS", osr.group(1) if osr else "?"),
        ("Kernel", sh("uname -r")),
        ("Session", f"{os.environ.get('XDG_SESSION_TYPE', '?')} / {os.environ.get('XDG_CURRENT_DESKTOP', '?')}"),
        ("CPU governor", read("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")),
        ("On AC power", read(glob.glob("/sys/class/power_supply/A*/online")[0]) if glob.glob("/sys/class/power_supply/A*/online") else "?"),
        ("Java", "<br>".join(sorted(javas))),
    ]
    out = ["# Test system", "", "Collected by tools/export.py. Edit the notes below by hand.", "",
           "| | |", "| --- | --- |"]
    out += [f"| {k} | {v} |" for k, v in rows]
    out += ["", "## Notes", "", "- Background load during the runs (browser, remote desktop, ...):",
            "- Anything unusual (thermal throttling, laptop on battery, ...):", ""]
    return "\n".join(out)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 1 or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", args[0]):
        sys.exit("usage: export.py <machine>-<cpu>-<gpu> [--all] [--force]")
    out = os.path.join(ROOT, "results", args[0])
    if os.path.exists(out) and "--force" not in sys.argv:
        sys.exit(f"{out} exists (use --force to overwrite)")
    logs = sorted(glob.glob(os.path.join(ROOT, "results", "raw", "*.log")))
    if "--all" not in sys.argv:
        logs = [p for p in logs if "-suite-" in os.path.basename(p)]
    if not logs:
        sys.exit("no logs in results/raw (run ./suite.sh first)")
    os.makedirs(out, exist_ok=True)

    renderers, javas = {}, set()
    with open(os.path.join(out, "summary.csv"), "w", newline="") as fs, \
            open(os.path.join(out, "samples.csv"), "w", newline="") as fp, \
            open(os.path.join(out, "threads.txt"), "w") as ft:
        ws, wp = csv.writer(fs), csv.writer(fp)
        ws.writerow(SUMMARY_COLS)
        wp.writerow(["run", "t_s", "fps", "chunk_updates", "heap_mb"])
        for p in logs:
            m = meta(p[:-4] + ".meta")
            try:
                r = analyze.analyze(p)
            except (AttributeError, ValueError):
                print(f"skipped (incomplete run): {os.path.basename(p)}")
                continue
            prime = m.get("prime", "")
            renderers[prime] = m.get("gl_renderer", "") or "?"
            # 26.x reports the backend and device it actually used (a Vulkan run is not described by glxinfo)
            renderer = m.get("device") or renderers[prime]
            javas.add(m.get("java_version", ""))
            rep = re.search(r"-r(\d+)$", m.get("label", ""))
            # gpu_busy_percent is read from the amdgpu card; it only describes the run when that card rendered it
            amd_run = re.search(r"AMD|Radeon|radeonsi", renderer, re.I) is not None
            ws.writerow([m.get("version"), prime, renderer, m.get("java"), m.get("variant", "default"),
                         (m.get("backend") or "OpenGL").lower(), m.get("view"), rep.group(1) if rep else "",
                         r["run"], f"{r['title']:.1f}", f"{r['create']:.1f}", f"{r['first10']:.0f}", f"{r['avg']:.0f}",
                         f"{r['last30']:.0f}", r["p5"], "" if r["chunk_updates"] is None else f"{r['chunk_updates']:.0f}",
                         "" if r["settled"] is None else r["settled"], r["heap_mb"],
                         "" if r["amd_busy"] is None or not amd_run else f"{r['amd_busy']:.0f}",
                         "" if r["cores"] is None else r["cores"]])
            t0 = None
            for s in re.finditer(r'\[BENCH\s+([\d,.]+)\] SAMPLE fps=(-?\d+) debug="-?\d+ fps, (-?\d+) chunk updates".*?heapMB=(\d+)',
                                 open(p).read()):
                ts = float(s.group(1).replace(",", "."))
                t0 = ts if t0 is None else t0
                wp.writerow([r["run"], f"{ts - t0:.0f}", s.group(2), s.group(3), s.group(4)])
            if r["threads"]:
                threads = re.sub(r"(\d),(\d)", r"\1.\2", r["threads"])  # logs from before run.sh pinned the locale
                ft.write(f"== {r['run']} (DRI_PRIME {prime}, Java {m.get('java')}, {m.get('variant', 'default')}) total {r['cores']} cores\n{threads}\n\n")
    md, path = system_md(renderers, javas), os.path.join(out, "system.md")
    old = open(path).read() if os.path.exists(path) else ""
    if "## Notes" in old:  # --force: keep the notes written by hand
        md = md[:md.index("## Notes")] + "## Notes" + old.split("## Notes", 1)[1]
    with open(path, "w") as f:
        f.write(md)
    print(f"wrote {out}: system.md, summary.csv, samples.csv, threads.txt - review system.md, fill in the notes")


if __name__ == "__main__":
    main()
