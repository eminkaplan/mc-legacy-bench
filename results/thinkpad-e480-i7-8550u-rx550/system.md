# Test system

Collected by tools/export.py. Edit the notes below by hand.

| | |
| --- | --- |
| Machine | LENOVO ThinkPad E480 (20KQS0E800) |
| CPU | Intel(R) Core(TM) i7-8550U CPU @ 1.80GHz, 8 threads |
| RAM | 23 GB |
| GPUs (lspci) | VGA compatible controller: Intel Corporation UHD Graphics 620 (rev 07)<br>Display controller: Advanced Micro Devices, Inc. [AMD/ATI] Lexa PRO [Radeon 540/540X/550/550X / RX 540X/550/550X] (rev c0) |
| OpenGL renderer per DRI_PRIME | 0: Mesa Intel(R) UHD Graphics 620 (KBL GT2)<br>1: AMD Radeon 500 Series (radeonsi, polaris12, ACO, DRM 3.61, 6.12.113+deb13-amd64) |
| Mesa / GL version | 4.6 (Compatibility Profile) Mesa 25.0.7-2+deb13u1 |
| OS | Debian GNU/Linux 13 (trixie) |
| Kernel | 6.12.113+deb13-amd64 |
| Session | wayland / KDE |
| CPU governor | powersave |
| On AC power | 1 |
| Java | openjdk version "1.8.0_504"<br>openjdk version "17.0.21" 2026-10-20<br>openjdk version "21.0.13" 2026-10-20 LTS<br>openjdk version "25.0.5" 2026-10-20 LTS |

## Notes

- Background load during the runs (browser, remote desktop, ...): RustDesk (~30% of one core) and Firefox (~15–25%) were running the whole time; load average 3–5 between runs. One run per combination, so treat differences under ~15% as noise.
- Anything unusual (thermal throttling, laptop on battery, ...): on AC, governor `powersave`, CPU at 65–80 °C. The display is wired to the Intel iGPU; the RX 550 has no outputs (muxless PRIME), so every RX 550 frame is copied to the iGPU.
- Run order: `GPUS="0 1" ./suite.sh` on 2026-10-27 between 03:25 and 05:02 local time. Ten runs were made again later the same morning after harness fixes, back to back within their version: 1.18.2 on the Intel GPU (the Java 17 run crashed in the harness's off-thread read of the chunk statistics), all four 26.3 runs (the default `inactivityFpsLimit:"afk"` capped FPS at 30), and all four b1.7.3 runs (its loop sleeps 10 ms a frame while the window has no focus).
- 26.3: both graphics backends ran on the GPU picked by DRI_PRIME (checked in each run's `Using graphics device` line). The OpenGL backend needs `SDL_VIDEO_FORCE_EGL=1` on X11/XWayland (set in `versions/26.3/version.env`).
- `extra/`: earlier series on 1.5.2 and 1.8.9 (Far view distance, pairs), taken before `suite.sh` existed with a 1 GB heap and without the Java 8/21 interleaving; not comparable to the main table.
