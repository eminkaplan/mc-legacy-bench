# mc-legacy-bench

How do old Minecraft versions run on today's hardware and today's Java? This repo holds a small, reproducible harness that launches official Minecraft clients from **Beta 1.7.3 (2011) to 26.3 (2026)** with no one at the keyboard, and a growing set of results from different machines.

**[→ All results: RESULTS.md](RESULTS.md)** · **[→ Add your machine or a version: CONTRIBUTING.md](CONTRIBUTING.md)**

## What a run does

1. Starts the client directly (no launcher, no account), in its own game directory under `work/`; your `~/.minecraft` is never touched.
2. Waits on the title screen for 3 s, then creates a fresh singleplayer world with a fixed seed (`1234567890`) through the game's own code path.
3. Locks keyboard/mouse input and the window size (854×480), so nothing moves the player.
4. Logs once a second for 90 s: the in-game F3 FPS counter, chunk work still pending, heap, player position; at the end, CPU time per thread.

The standard protocol (`suite.sh`) runs every version × Java pair (and graphics backend, for 26.3) back to back on each GPU: Normal view distance (8 chunks), unlimited FPS, a 2 GB heap.

## First results: ThinkPad E480

i7-8550U (4 cores / 8 threads) · Intel UHD 620 + Radeon RX 550 (muxless) · Debian 13, KDE Wayland, Mesa 25.0.7 · one run per cell · [full system and notes](results/thinkpad-e480-i7-8550u-rx550/system.md)

| Version (released) | Own Java | FPS Intel UHD 620<br>own Java / Java 21 | FPS Radeon RX 550<br>own Java / Java 21 | Title screen (s) | World creation (s) | CPU cores used<br>own Java → Java 21 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| b1.7.3 (2011-07) | 8 | 107 / 98 | 180 / 185 | 2.1 | 6.9 | 1.3 → 1.3 |
| 1.2.5 (2012-03) | 8 | 51 / 49 | 78 / 75 | 2.4 | 9.8 | 1.4 → 1.4 |
| 1.5.2 (2013-04) | 8 | 90 / 85 | 93 / 85 | 3.6 | 8.9 | 1.9 → 1.7 |
| 1.7.10 (2014-05) | 8 | 101 / 110 | 143 / 146 | 3.9 | 11.8 | 2.0 → 1.9 |
| 1.8.9 (2015-12) | 8 | 113 / 119 | 116 / 136 | 8.9 | 14.9 | 2.8 → 2.0 |
| 1.12.2 (2017-09) | 8 | 146 / 147 | 128 / 137 | 13.1 | 20.9 | 2.7 → 2.1 |
| 1.14.4 (2019-07) | 8 | 217 / 224 | 294 / 253 | 18.0 | 50.5 | 2.8 → 2.4 |
| 1.16.5 (2021-01) | 8 | 158 / 143 | 191 / 199 | 19.5 | 72.9 | 2.8 → 2.5 |
| 1.18.2 (2022-02) | 17 | 97 / 84 | 120 / 137 | 47.6 | 143.0 | 2.9 → 2.8 |
| 26.3 (2026-09) | 25 | 28 / 28 ¹ | 47 / 44 ¹ | 26.6 | 21.0 | 6.0 |

FPS: mean of the last 30 s. Title screen, world creation and cores: median of the version's runs on both GPUs. ¹ 26.3 runs on Java 25 only; the two numbers are its OpenGL / Vulkan backends.

1. **Every version runs on 2026 software.** Fifteen years of clients start on Mesa 25 and current Temurin builds; the LWJGL 2 versions (up to 1.12.2) also run on Java 21 through a small shim (see below). The fixed-function OpenGL of 2011 works fine on Mesa's compatibility profile.
2. **FPS peaked with 1.14.4 and has fallen since.** 1.16.5 is 20–35% slower, 1.18.2 about half, and 26.3 is the slowest of all: 28 FPS on the Intel GPU, a quarter of what Beta 1.7.3 gets on the same machine.
3. **26.3 fills the CPU.** With the player standing still it keeps about 6 of the 8 hardware threads busy, a minute after entering the world:
   - its `Worker-Main` pool uses ~2 cores;
   - the JIT compiler threads use 1.5–2.3 cores;
   - the server thread uses 0.6 cores and the garbage collector 0.4.

   Its render thread uses less than half a core and the RX 550 is ~15% busy, and OpenGL and Vulkan give the same FPS. The limit is the CPU as a whole, not the GPU or the graphics API.
4. **Multithreading came in steps.**
   - b1.7.3 and 1.2.5 do everything on one main thread (1.2–1.4 cores including the JIT).
   - 1.3 added the integrated server thread (1.5.2: 0.2 cores of server, ~1.8 in total).
   - 1.8 moved chunk building to "Chunk Batcher" threads, which use under 0.1 cores while the player stands still.
   - From 1.14 the server thread doubles to ~0.55 cores, and from 1.16 a `Worker-Main` pool appears (0.3 cores in 1.18.2).
   - Through 1.18.2 the main thread stays the bottleneck: it uses 70–96% of a core in every run. 26.3 is the first version here where it does not (~45%) and the rest of the CPU is full instead.
5. **Startup and world creation grew twentyfold up to 1.18.2**, from 2 s to 48 s to the title screen and from 7 s to 143 s for a new world. Every version generates a spawn area before the player enters, but from 1.14 on, with the new chunk system, that takes far longer (1.18.2 logs "Preparing spawn area" for over two minutes). 26.3 only loads the chunks around the player ("Loading 48 chunks for player spawn") and is back to 21 s.
6. **The discrete GPU helps in some versions and not in others.**
   - The RX 550 gains: b1.7.3 +70–90%, 1.2.5 +50%, 1.7.10 +35–40%, 1.14.4 +15–35%, 1.16.5 +20–40%, 1.18.2 +25–60%, 26.3 +60%.
   - It brings nothing beyond noise in 1.5.2, 1.8.9 and 1.12.2; 1.12.2 is even slightly slower on it.
   - In every version the main thread uses less CPU on the RX 550 (~75% of a core instead of ~90%), because Mesa's radeonsi driver moves work to its own threads (`java:gl`, `java:cs`). Why that turns into more FPS in some versions and not in others, we have not measured.
   - Startup and world creation are consistently slower on the RX 550 (1.12.2: 14–18 s vs 10–12 s to the title screen). We did not look into why; every RX 550 frame is copied to the Intel GPU, which drives the display.
7. **1.2.5 has half the FPS of Beta 1.7.3** (51 vs 107 on Intel). Both keep the main thread fully busy, so 1.2.5 simply does more work per frame. 1.2 introduced the Anvil format and doubled the world height to 256 blocks; we have not profiled which change costs the frames.
8. **Java 21 changes FPS by no more than the noise of a single run, but often uses less CPU.** In 1.8.9–1.16.5 the total drops by 0.3–0.8 cores, almost all of it JIT compiler time (Java 8 kept three C2 compiler threads busy; Java 21 adds compiler threads on demand and used one).

Run-to-run spread on this machine is about ±10–15%, and a remote-desktop client and a browser ran in the background (details in `system.md`).

### Things that change the result by 2–4×

Found while building the harness; each is handled by the standard protocol, and each is worth knowing if you benchmark Minecraft yourself:

- **A 1 GB heap halves FPS from 1.18 on.** In a short test on the Intel GPU, 1.18.2 ran at 20–23 FPS with `-Xmx1G` and 43–57 with 2 GB. The protocol uses 2 GB.
- **26.x caps FPS at 30 when it thinks you are AFK.** The default `inactivityFpsLimit:"afk"` (1.21.2+) kicks in after 30 s without input, so any unattended benchmark measures 30 FPS. The harness sets it to `minimized`.
- **b1.7.3 sleeps 10 ms a frame while its window has no focus.** The harness window is not focusable (so input cannot reach it), so the harness sets LWJGL's focus flag instead. Without it, b1.7.3 measures 44 FPS instead of 107.
- **26.x's OpenGL backend fails on X11/XWayland without EGL** ("Could not retrieve OpenGL functions") and silently falls back to Vulkan. `SDL_VIDEO_FORCE_EGL=1` fixes it, and each run records the backend and device that actually ran.
- **From 1.8 on, Far view makes the integrated server follow the client's 16-chunk view** ("Changing view distance to 16") and generate ~2.5× more terrain ("Can't keep up!"). 1.5.2 stays at 10. So view distance is not comparable across versions at Far, and the protocol uses Normal.
- **Background load drifts results.** The same test drifted ~25% over an hour with a browser and remote desktop running. `suite.sh` runs the pairs back to back for that reason.

Earlier series (Far view, 1.5.2 vs 1.8.9 pairs, 1 GB heap) are in [`results/thinkpad-e480-i7-8550u-rx550/extra/`](results/thinkpad-e480-i7-8550u-rx550/extra/).

## Quick start

Needs Linux x86_64, bash, curl, python3, gcc, `glxinfo` (mesa-utils) and X11 or XWayland. **You must own Minecraft:** the game files are not in this repo; `setup.sh` fetches them from Mojang's servers and checks every file against the SHA1 in Mojang's manifest. All ten versions take about 2 GB under `work/` (JDKs, clients, assets without sounds).

```bash
./setup.sh                          # every version in versions/ (or: ./setup.sh 1.5.2 26.3)
GPUS="0 1" ./suite.sh               # standard protocol, ~2 min per run; GPUS = DRI_PRIME values ("0" on a single-GPU machine)
python3 tools/export.py <machine>-<cpu>-<gpu>
python3 tools/aggregate.py          # rebuilds RESULTS.md
```

A single run: `./run.sh <version> <label> <DRI_PRIME> <java> [seconds] [normal|far] [variant]`, then `python3 tools/analyze.py results/raw/<version>-<label>.log`. Keep your hands off the keyboard and mouse while it runs.

## How it works

Each version is a folder in [`versions/`](versions/): a `version.env` (Java versions, options.txt, launch arguments) and a `Bench.java` harness that runs inside the game.

- **LWJGL 2 clients (b1.7.3 – 1.12.2)** are compiled straight against the obfuscated client. Names come from the MCP mappings (1.7.10, 1.8.9, 1.12.2) or from `javap` (older); each file lists the ones it uses at the top. The world is created on the game's main thread, through `addScheduledTask` from 1.7.10 on, or from a `GuiScreen.updateScreen` call before that. A screen that swallows input stays open over the world.
- **LWJGL 3 clients (1.14.4+)** share [`common/modern/`](common/modern/). It reads Mojang's official mappings (shipped from 1.14.4 on; 26.x is not obfuscated) so each version's harness uses real names and only implements world creation. It locks the window through GLFW ([`common/glfw/`](common/glfw/)) or, for 26.x, SDL3 ([`common/sdl/`](common/sdl/)).
- **Per-thread CPU time** comes from `/proc/self/task/*/stat` ([`common/bench/ThreadCpu.java`](common/bench/ThreadCpu.java)), from entering the world to the end of the run.
- **Signed client jars** (1.8.9) get a local unsigned copy, because the harness shares the client's default package.

### Java 21 and LWJGL 2

LWJGL 2's `liblwjgl64.so` links against the `SUNWprivate_1.1` symbol version of Java 8's `libjawt.so`. Java 9+ no longer has it, and the game does not start:

```
liblwjgl64.so: libjawt.so: version `SUNWprivate_1.1' not found
```

`setup.sh` copies the natives to `natives-shim/` and renames that dependency to the same-length `libjawx.so`. [`shim/jawx.c`](shim/jawx.c) is a few lines built under that name: it provides `JAWT_GetAWT@SUNWprivate_1.1` and forwards to the JDK's real `libjawt.so`. The original natives are untouched. This is also why launchers download Java 8 for old versions.

## License

The code in this repo is [MIT](LICENSE)-licensed. Minecraft belongs to Mojang Studios / Microsoft; this project is not affiliated with them and does not distribute game files.
