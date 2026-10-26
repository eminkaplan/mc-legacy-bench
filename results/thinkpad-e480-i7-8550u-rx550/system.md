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
| Java | openjdk version "1.8.0_504" (Temurin)<br>openjdk version "21.0.13" 2026-10-20 LTS (Temurin) |

## Notes

- Background load during the runs (browser, remote desktop, ...): RustDesk (~30% CPU) and Firefox were running; load average ~6. The same 1.5.2 test drifted ~25% slower over an hour, so only runs taken back to back are compared.
- Anything unusual (thermal throttling, laptop on battery, ...): CPU around 80 °C. Runs were made before suite.sh existed, with the same harness, settings and back-to-back order (Java 8 then 21 per version and GPU). The display is wired to the Intel iGPU; the RX 550 has no outputs (muxless PRIME).
- `extra/`: earlier series (Far view distance, 1.5.2 vs 1.8.9 pairs) described in the main README.
