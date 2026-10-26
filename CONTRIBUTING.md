# Contributing

The goal is a public record of how old Minecraft versions run across many machines: different CPUs, GPUs, drivers and Java versions, collected over time. The most useful contribution is **your machine's results**. New Minecraft versions and harness fixes are welcome too.

## Submitting results

### 1. Prepare

- Linux x86_64 with bash, curl, python3, gcc and X11 or XWayland (`glxinfo` from `mesa-utils` is optional but fills in the GPU name).
- You must own Minecraft. Nothing from Mojang is committed; `setup.sh` downloads the official files on your machine.
- Fork this repo on GitHub, then clone your fork:

```bash
git clone https://github.com/<you>/mc-legacy-bench.git
cd mc-legacy-bench
./setup.sh
```

### 2. Measure

Make the machine as quiet as you can: close the browser, chat apps and remote-desktop tools, plug a laptop into AC, and let it cool down. Background load easily moves results by 10–25%.

```bash
GPUS="0" ./suite.sh                 # single GPU
GPUS="0 1" ./suite.sh               # hybrid laptop: integrated (0) and discrete (1) through DRI_PRIME
REPEAT=2 GPUS="0 1" ./suite.sh      # better: two passes, combined by median
```

Each run takes about 2 minutes (4 runs per GPU per pass). A Minecraft window opens and closes by itself; **don't touch the keyboard or mouse** until the suite prints `suite done`.

Check that every run finished:

```bash
python3 tools/analyze.py results/raw/*-suite-*.log
```

Every line should show a `create=` time and FPS values. A run that crashed or was disturbed can be repeated with `./suite.sh` (or a single `./run.sh` with the same label).

### 3. Export

```bash
python3 tools/export.py <machine>-<cpu>-<gpu>      # e.g. desktop-r5-5600-rx6600; no personal names
python3 tools/aggregate.py
```

`export.py` writes `results/<name>/`:

| File | Content |
| --- | --- |
| `system.md` | CPU, RAM, GPUs, OpenGL renderer per `DRI_PRIME`, Mesa, OS, kernel, session, governor, Java versions (collected automatically) |
| `summary.csv` | one row per run |
| `samples.csv` | one row per second: FPS, chunk updates, heap |
| `threads.txt` | CPU time per thread |

Open `system.md` and **fill in the Notes**: what was running in the background, whether the laptop throttled, anything unusual. Check that nothing in it is private to you. Raw logs (`results/raw/`) stay out of git because they contain absolute paths.

### 4. Open a pull request

```bash
git checkout -b results/<machine>-<cpu>-<gpu>
git add results/<machine>-<cpu>-<gpu> RESULTS.md
git commit -m "Add results: <CPU> + <GPU>"
git push -u origin results/<machine>-<cpu>-<gpu>
```

Then open a pull request against `main` on GitHub. The template asks for a short checklist. One folder per machine; if the name is taken, add `-2`. For another machine (or a big driver/OS change on the same one), use a new folder name.

## Rules for results

- Only `suite.sh` runs go into `summary.csv`; other experiments (Far view, other window sizes) belong in `results/<name>/extra/` with a sentence in `system.md` saying what they are.
- Don't edit numbers by hand. If a run looks wrong, rerun it.
- Don't change `run.sh` settings (window size, view distance, seed, `-Xmx1G`, run length) for a submission; results are only comparable under the same protocol. Propose protocol changes as a separate pull request.

## Adding a Minecraft version

1. Create `v<version>/Bench.java`, modelled on the closest existing harness. It must create the world with the fixed seed, lock input and window size, and log `TITLE_SCREEN`, `CREATE_WORLD returned after`, `IN_WORLD`, one `SAMPLE` line per second, the `ThreadCpu` report and `DONE` in the same format, so `tools/analyze.py` reads it unchanged.
2. Find the obfuscated names you need (MCP/MCPConfig/Mojang mappings or `javap`) and list them at the top of the file.
3. Teach `setup.sh` (download, compile) and `run.sh` (options.txt, main class, arguments) about the version, and add it to `suite.sh`.
4. In the pull request, include one machine's results for the new version and say which Java versions it supports.

## Code changes

Keep scripts in plain bash/python3 with no extra dependencies. Run `./setup.sh` and a short `./run.sh 1.5.2 test 0 8 20` and `./run.sh 1.8.9 test 0 21 20` before opening the pull request.
