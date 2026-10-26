# mc-legacy-bench

How do old Minecraft versions run on today's hardware and today's Java? This repo holds a small, reproducible harness that launches the official **1.5.2** (2013) and **1.8.9** (2015) clients with no one at the keyboard, and a growing set of results from different machines.

**[→ All results: RESULTS.md](RESULTS.md)** · **[→ Add your machine: CONTRIBUTING.md](CONTRIBUTING.md)**

## What a run does

1. Starts the client directly (no launcher), in its own game directory under `work/`; your `~/.minecraft` is never touched.
2. Waits on the title screen for 3 s, then creates a fresh singleplayer world with a fixed seed (`1234567890`) through the game's own code path.
3. Once in the world, locks keyboard/mouse input and the window size (854×480), so nothing moves the player.
4. Logs once a second for 90 s: the in-game F3 FPS counter, chunk updates, heap, player position; at the end, CPU time per thread.

The standard protocol (`suite.sh`) runs every version × Java 8/21 pair back to back on each GPU, so the runs compared were taken minutes apart.

## First results: ThinkPad E480

i7-8550U (4 cores / 8 threads) · Intel UHD 620 + Radeon RX 550 (muxless) · Debian 13, KDE Wayland, Mesa 25.0.7 · [full system](results/thinkpad-e480-i7-8550u-rx550/system.md)

| Steady FPS (last 30 s, Normal view) | Java 8 | Java 21 |
| --- | ---: | ---: |
| 1.5.2 · Intel UHD 620 | 62 | 69 |
| 1.5.2 · Radeon RX 550 | 79 | 76 |
| 1.8.9 · Intel UHD 620 | 97 | 94 |
| 1.8.9 · Radeon RX 550 | 100 | 125 |

Run-to-run spread on this machine was about ±10–15%.

1. **The bottleneck is the main thread, not the GPU.** The RX 550 sat 25–45% busy; the main thread used 75–90% of a core in both versions. 1.5.2 uses about 2 of the 8 hardware threads.
2. **1.8 is faster because chunk building left the main thread, not because it uses many cores.** 1.5.2 rebuilds one chunk per frame on the main thread and never stops, even with the player standing still (F3 often reads "42 fps, 42 chunk updates"). 1.8.9 finishes in ~25 s; its "Chunk Batcher" threads use only ~5% of a core.
3. **The AMD edge likely comes from the driver.** Mesa's radeonsi moves work to its own threads (`java:gl`, `java:cs`) and drops the main thread from ~90% to ~75%. Shared power budget between CPU and iGPU may add to it; not measured.
4. **Java 21 does not change FPS beyond noise, but uses fewer CPU cores.** Java 8's three C2 compiler threads use more than a full core in 1.8.9 throughout the run; Java 21's single one uses about a third of that (2.7–2.9 → 1.9–2.2 cores total). Startup is 0.4–1.8 s slower on Java 21.
5. **World generation is ~1.7× slower in 1.8.9** (≈9 s vs ≈16 s): the 1.7 terrain update costs more. The same seed gives different terrain in the two versions.
6. **At Far view distance 1.8.9 overloads its own integrated server.** From 1.8 the server follows the client's 16-chunk view ("Changing view distance to 16, from 10") and generates ~2.5× more terrain ("Can't keep up! 51 s behind"); 1.5.2 stays at 10. Hence the standard protocol uses Normal.
7. **2013 software still runs on 2026 drivers.** LWJGL 2 and the fixed-function OpenGL path ran flawlessly on Mesa 25's compatibility profile; only Mojang's long-gone servers (assets.minecraft.net, the S3 resource bucket) fail, and the game only logs it.

Limits: one run per combination; a remote-desktop client and a browser ran in the background, and the same test drifted ~25% over an hour, so only back-to-back runs are compared. Earlier series (Far view, 1.5.2 vs 1.8.9 pairs) are in [`results/thinkpad-e480-i7-8550u-rx550/extra/`](results/thinkpad-e480-i7-8550u-rx550/extra/).

## Quick start

Needs Linux x86_64, bash, curl, python3, gcc and X11 or XWayland. **You must own Minecraft:** the game files are not in this repo; `setup.sh` fetches them from Mojang's servers and checks every file against the SHA1 in Mojang's manifest.

```bash
./setup.sh                          # Temurin 8 + 21, both clients, the Java 21 shim, the harness -> work/
GPUS="0 1" ./suite.sh               # standard protocol; GPUS = DRI_PRIME values ("0" on a single-GPU machine)
python3 tools/export.py <machine>-<cpu>-<gpu>
python3 tools/aggregate.py          # rebuilds RESULTS.md
```

A single run: `./run.sh <1.5.2|1.8.9> <label> <DRI_PRIME> <8|21> [seconds] [normal|far]`, then `python3 tools/analyze.py results/raw/*.log`. Keep your hands off the keyboard and mouse while it runs.

## How it works

- **Harness inside the game** ([`v1.5.2/Bench.java`](v1.5.2/Bench.java), [`v1.8.9/Bench.java`](v1.8.9/Bench.java)). Obfuscated names were found with `javap` for 1.5.2 and from the MCP `stable_22` mappings for 1.8.9; each file lists them at the top. 1.8.9 runs its work on the main thread through `addScheduledTask`; 1.5.2 has no such hook, so the world is created from a `GuiScreen.updateScreen` call, which the game makes on the main thread.
- **Per-thread CPU time** comes from `/proc/self/task/*/stat` ([`common/ThreadCpu.java`](common/ThreadCpu.java)), from entering the world to the end of the run.
- **1.8.9's `client.jar` is signed by Mojang.** The harness shares its default package, so `setup.sh` makes a local unsigned copy.

### Java 21 and LWJGL 2

LWJGL 2's `liblwjgl64.so` links against the `SUNWprivate_1.1` symbol version of Java 8's `libjawt.so`. Java 9+ no longer has it, and the game does not start:

```
liblwjgl64.so: libjawt.so: version `SUNWprivate_1.1' not found
```

`setup.sh` copies the natives to `natives-j21/` and renames that dependency to the same-length `libjawx.so`. [`shim/jawx.c`](shim/jawx.c) is a few lines built under that name: it provides `JAWT_GetAWT@SUNWprivate_1.1` and forwards to the JDK's real `libjawt.so`. The original natives are untouched. This is also why launchers download Java 8 for old versions.

## License

The code in this repo is [MIT](LICENSE)-licensed. Minecraft belongs to Mojang Studios / Microsoft; this project is not affiliated with them and does not distribute game files.
