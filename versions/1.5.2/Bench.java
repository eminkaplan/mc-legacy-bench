import bench.ThreadCpu;
import java.io.File;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;

// Headless benchmark harness for Minecraft 1.5.2 (obfuscated names):
//   Minecraft.x()            getMinecraft      Minecraft.s : axr  currentScreen
//   Minecraft.a(S,S,aai)     launchIntegratedServer
//   Minecraft.ad (static)    debugFPS          Minecraft.L  : debug string ("N fps, M chunk updates")
//   Minecraft.e / .g         theWorld / thePlayer
//   axr.c()                  GuiScreen.updateScreen, called from runTick on the main thread
public class Bench {
    static volatile long tCreateStart, tCreateEnd;
    static final long T0 = System.nanoTime();
    static int W, H;

    static double sec(long t) { return (t - T0) / 1e9; }
    static void log(String s) { System.out.printf("[BENCH %8.3f] %s%n", sec(System.nanoTime()), s); System.out.flush(); }

    // Runs on the main game thread on the first tick it is displayed.
    public static class CreateWorldScreen extends axr {
        private boolean fired;
        public void c() {
            if (fired) return;
            fired = true;
            long seed = Long.getLong("bench.seed", 1234567890L);
            String dir = "bench-" + System.nanoTime();
            log("CREATE_WORLD start seed=" + seed + " dir=" + dir);
            tCreateStart = System.nanoTime();
            Minecraft.x().a(dir, "Bench", new aai(seed, aaj.a("survival"), true, false, aal.a("default")));
            tCreateEnd = System.nanoTime();
            log(String.format("CREATE_WORLD returned after %.3f s", (tCreateEnd - tCreateStart) / 1e9));
        }
    }

    // Shown over the world once loaded: does not pause the game and swallows all keys,
    // so stray keyboard/mouse input cannot move the player or toggle F3.
    public static class LockScreen extends axr {
        public boolean f() { return false; }
        protected void a(char c, int key) { }
    }

    // Keep the AWT frame at a fixed inner size, non-resizable and non-focusable.
    static void lockWindow(final Minecraft mc, final int w, final int h) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                java.awt.Window win = javax.swing.SwingUtilities.getWindowAncestor(mc.m);
                if (!(win instanceof java.awt.Frame)) return;
                java.awt.Frame fr = (java.awt.Frame) win;
                fr.setExtendedState(java.awt.Frame.NORMAL);
                fr.setResizable(false);
                fr.setFocusableWindowState(false);
                if (mc.m.getWidth() != w || mc.m.getHeight() != h) {
                    mc.m.setPreferredSize(new java.awt.Dimension(w, h));
                    fr.pack();
                }
            }
        });
    }

    static Field field(String name) throws Exception {
        Field f = Minecraft.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    public static void main(String[] args) throws Exception {
        File gameDir = new File(System.getProperty("bench.gamedir")).getAbsoluteFile();
        field("an").set(null, gameDir);
        final int measureSecs = Integer.getInteger("bench.seconds", 60);
        W = Integer.getInteger("bench.w", 854);
        H = Integer.getInteger("bench.h", 480);

        Thread mon = new Thread(new Runnable() {
            public void run() {
                try {
                    Field fps = field("ad"), dbg = field("L"), screen = field("s");
                    Minecraft mc;
                    while ((mc = Minecraft.x()) == null || screen.get(mc) == null) Thread.sleep(50);
                    log("TITLE_SCREEN reached (" + screen.get(mc).getClass().getName() + ")");
                    Thread.sleep(3000);
                    CreateWorldScreen cw = new CreateWorldScreen();
                    cw.a(mc, mc.c, mc.d);
                    screen.set(mc, cw);
                    lockWindow(mc, W, H);
                    while (tCreateEnd == 0) Thread.sleep(50);
                    long tIn = 0;
                    java.util.Map<String, Long> cpu0 = null;
                    Runtime rt = Runtime.getRuntime();
                    for (int i = 0; ; i++) {
                        Thread.sleep(1000);
                        boolean inWorld = mc.e != null && mc.g != null;
                        if (inWorld && tIn == 0) { tIn = System.nanoTime(); cpu0 = ThreadCpu.snapshot(); log("IN_WORLD"); }
                        if (inWorld && !(screen.get(mc) instanceof LockScreen)) {
                            LockScreen ls = new LockScreen();
                            ls.a(mc, mc.c, mc.d);
                            screen.set(mc, ls);
                        }
                        lockWindow(mc, W, H);
                        log(String.format("SAMPLE fps=%d debug=\"%s\" inWorld=%b heapMB=%d win=%dx%d pos=%s",
                                fps.getInt(null), dbg.get(mc), inWorld, (rt.totalMemory() - rt.freeMemory()) >> 20,
                                mc.c, mc.d, mc.g == null ? "-" : String.format("%.1f,%.1f,%.1f", mc.g.u, mc.g.v, mc.g.w)));
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
        log("LAUNCH java=" + System.getProperty("java.version"));
        Minecraft.main(args);
    }
}
