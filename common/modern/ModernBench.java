package bench;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The shared run for LWJGL 3 clients (1.13+): wait for the title screen, lock the window and input (GLFW or SDL,
// see WindowLock), create the world on the main thread, then log one SAMPLE line a second in the format tools/analyze.py reads.
public class ModernBench {
    static volatile long tCreateStart, tCreateEnd;
    static final long T0 = System.nanoTime();
    static int W, H;
    static volatile int winW, winH;

    static void log(String s) {
        System.out.printf("[BENCH %8.3f] %s%n", (System.nanoTime() - T0) / 1e9, s);
        System.out.flush();
    }

    static int find(String s, String regex) {
        Matcher mt = Pattern.compile(regex).matcher(s);
        return mt.find() ? Integer.parseInt(mt.group(1)) : -1;
    }

    static WindowLock windowLock;

    static void lockWindow(long h) {
        int[] size = windowLock.lock(h, W, H);
        winW = size[0];
        winH = size[1];
    }

    public static void run(final ModernVersion v, String[] args) throws Exception {
        final int measureSecs = Integer.getInteger("bench.seconds", 60);
        final long seed = Long.getLong("bench.seed", 1234567890L);
        W = Integer.getInteger("bench.w", 854);
        H = Integer.getInteger("bench.h", 480);
        final Mapped m = Mapped.load();
        windowLock = (WindowLock) Class.forName(System.getProperty("bench.windowlock", "bench.GlfwLock")).getDeclaredConstructor().newInstance();
        final Class<?> mcClass = m.cls(ModernVersion.MC);

        Thread mon = new Thread(new Runnable() {
            public void run() {
                try {
                    Object mc;
                    while ((mc = m.call(null, ModernVersion.MC, "getInstance()")) == null || !v.atTitle(m, mc)) Thread.sleep(50);
                    log("TITLE_SCREEN reached");
                    Thread.sleep(3000);
                    final Object fmc = mc;
                    final long handle = v.windowHandle(m, mc);
                    ((Executor) mc).execute(new Runnable() {
                        public void run() {
                            try {
                                lockWindow(handle);
                                String dir = "bench-" + System.nanoTime();
                                log("CREATE_WORLD start seed=" + seed + " dir=" + dir);
                                tCreateStart = System.nanoTime();
                                v.createWorld(m, fmc, dir, seed);
                                log(String.format("create call returned after %.3f s", (System.nanoTime() - tCreateStart) / 1e9));
                            } catch (Throwable e) {
                                e.printStackTrace();
                                Runtime.getRuntime().halt(3);
                            }
                            tCreateEnd = System.nanoTime();
                        }
                    });
                    while (tCreateEnd == 0 || !v.inWorld(m, mc)) Thread.sleep(50);
                    log(String.format("CREATE_WORLD returned after %.3f s", (System.nanoTime() - tCreateStart) / 1e9));
                    long tIn = 0;
                    Map<String, Long> cpu0 = null;
                    Runtime rt = Runtime.getRuntime();
                    for (int i = 0; ; i++) {
                        Thread.sleep(1000);
                        boolean inWorld = v.inWorld(m, mc);
                        if (inWorld && tIn == 0) { tIn = System.nanoTime(); cpu0 = ThreadCpu.snapshot(); log("IN_WORLD"); }
                        ((Executor) mc).execute(new Runnable() { public void run() { lockWindow(handle); } });
                        String fl = v.fpsLine(m, mc), cl = "";
                        // Read off the render thread: the chunk statistics can be mid-update, so a failed read only loses this sample's pending count.
                        try { if (inWorld) cl = v.chunkLine(m, mc); } catch (Exception e) { log("chunk statistics unavailable: " + e.getCause()); }
                        int fps = find(fl, "^(\\d+) fps"), cu = find(fl, "\\((\\d+) chunk update"), pending = find(cl, "pC: (\\d+)");
                        Object player = m.get(mc, ModernVersion.MC, "player");
                        String pos = "-";
                        if (player != null) {
                            double[] p = v.pos(m, player);
                            pos = String.format("%.1f,%.1f,%.1f", p[0], p[1], p[2]);
                        }
                        log(String.format("SAMPLE fps=%d debug=\"%d fps, %d chunk updates\" pending=%d inWorld=%b heapMB=%d win=%dx%d pos=%s",
                                fps, fps, cu, pending, inWorld, (rt.totalMemory() - rt.freeMemory()) >> 20, winW, winH, pos));
                        if (tIn != 0 && (System.nanoTime() - tIn) / 1e9 >= measureSecs) break;
                        if (i > 600) { log("TIMEOUT"); break; }
                    }
                    log(ThreadCpu.report(cpu0, ThreadCpu.snapshot(), (System.nanoTime() - tIn) / 1e9));
                    log("DONE");
                    Runtime.getRuntime().halt(0);
                } catch (Throwable t) {
                    t.printStackTrace();
                    Runtime.getRuntime().halt(2);
                }
            }
        }, "bench-monitor");
        mon.setDaemon(true);
        mon.start();
        log("LAUNCH java=" + System.getProperty("java.version") + " client=" + mcClass.getName());
        net.minecraft.client.main.Main.main(args);
    }
}
