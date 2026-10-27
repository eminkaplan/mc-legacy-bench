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

A pass runs every version in `versions/` with each of its Java versions (and, for 26.x, both graphics backends): 20 runs per GPU, about an hour per GPU, because modern versions take minutes to generate a world. `VERSIONS="1.5.2 1.8.9" ./suite.sh` runs a subset. A Minecraft window opens and closes by itself; **don't touch the keyboard or mouse** until the suite prints `suite done`.

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
- Don't change the protocol (window size, view distance, seed, `-Xmx2G` heap, run length, the options each `version.env` writes) for a submission; results are only comparable under the same protocol. Propose protocol changes as a separate pull request.

## Adding a Minecraft version

Each version is one folder, `versions/<id>/`, picked up by `setup.sh`, `run.sh` and `suite.sh` with no other changes:

| File | Content |
| --- | --- |
| `version.env` | `JAVAS` (Java versions to run), `JAVAC_RELEASE`, flags (`JAWT_SHIM=1` for LWJGL 2 on Java 9+, `UNSIGN=1` for a signed client jar, `MODERN=1` + `WINDOW_API=glfw\|sdl` for LWJGL 3 clients), optional `VARIANTS` and `RUN_ENV`, and two functions: `bench_options` (writes options.txt for `normal`/`far` and the variant) and `bench_args` (main class + arguments) |
| `Bench.java` | the harness: create the world, report FPS |

Two kinds of harness exist:

- **LWJGL 2 clients (b1.7.3 – 1.12.2):** a class in the default package compiled straight against the obfuscated client, as in `versions/1.8.9/Bench.java` (with `addScheduledTask`, 1.7.10+) or `versions/1.5.2/Bench.java` (without it: the world is created from a `GuiScreen.updateScreen`, which the game calls on its main thread; before 1.3 through `startWorld`, see `versions/1.2.5`). Names come from MCP mappings where they exist, otherwise from `javap`; list them at the top of the file.
- **LWJGL 3 clients (1.13+):** `package bench; class Bench extends ModernVersion` that only implements `createWorld` (and overrides what moved: see `versions/1.14.4` and `versions/26.3`). `common/modern` does the rest: it reads Mojang's official mappings (1.14.4+) so the code uses real names, locks the window through GLFW or SDL, and logs the samples. Unobfuscated clients (26.x) need no mappings.

Every harness logs `TITLE_SCREEN`, `CREATE_WORLD returned after`, `IN_WORLD`, one `SAMPLE` line per second, the `ThreadCpu` report and `DONE` in the same format, so `tools/analyze.py` reads it unchanged. Things that bit us, worth checking for a new version: a first-launch screen in front of the title screen (`onboardAccessibility:false`), options.txt without a `version:` line being "upgraded" and reset (26.x), the 1 GB heap that halves FPS from 1.18 on, a backend that silently falls back to another one (26.x on X11), and FPS limits that kick in because nobody touches the input (`inactivityFpsLimit:"afk"`, 1.21.2+, caps FPS at 30 after 30 s), and game loops that sleep while the window has no focus (b1.7.3: 10 ms a frame, so its harness keeps the window focused). If FPS looks low, check that the main thread is actually busy (the `THREADS` report, or a few `jstack` samples). Look at the FPS curve of a full 90 s run, not only a short one.

In the pull request, include one machine's results for the new version and say which Java versions it supports.

## Code changes

Keep scripts in plain bash/python3 with no extra dependencies. Run `./setup.sh` and a short run of every version you touched (`./run.sh <version> test 0 <java> 20`) before opening the pull request.
