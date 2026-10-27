import bench.ThreadCpu;
import java.lang.reflect.Field;
import java.util.Map;

// Headless benchmark harness for Minecraft 1.8.9 (obfuscated names, resolved via MCP stable_22):
//   ave = Minecraft          ave.A()  getMinecraft      ave.m : axu  currentScreen
//   ave.a(Runnable)          addScheduledTask (runs on the main thread)
//   ave.a(S,S,adp)           launchIntegratedServer      adp = WorldSettings, adr = WorldType
//   ave.ao (static)          debugFPS                    ave.C : debug string
//   ave.f / ave.h            theWorld / thePlayer        pk.s/t/u = Entity posX/Y/Z
//   axu.d()                  doesGuiPauseGame            axu.a(char,int) keyTyped
public class Bench {
    static volatile long tCreateStart, tCreateEnd;
    static final long T0 = System.nanoTime();
    static int W, H;

    static double sec(long t) { return (t - T0) / 1e9; }
    static void log(String s) { System.out.printf("[BENCH %8.3f] %s%n", sec(System.nanoTime()), s); System.out.flush(); }

    // Shown over the world once loaded: does not pause the game and swallows all keys.
    public static class LockScreen extends axu {
        public boolean d() { return false; }
        protected void a(char c, int key) { }
    }

    static Field field(String name) throws Exception {
        Field f = ave.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    // Keep the LWJGL window at a fixed size and non-resizable (1.8 draws straight into a Display, no AWT frame).
    static void lockWindow() {
        try {
            org.lwjgl.opengl.Display.setResizable(false);
            if (org.lwjgl.opengl.Display.getWidth() != W || org.lwjgl.opengl.Display.getHeight() != H)
                org.lwjgl.opengl.Display.setDisplayMode(new org.lwjgl.opengl.DisplayMode(W, H));
        } catch (Exception e) {
            log("lockWindow failed: " + e);
        }
    }

    public static void main(String[] args) throws Exception {
        final int measureSecs = Integer.getInteger("bench.seconds", 60);
        W = Integer.getInteger("bench.w", 854);
        H = Integer.getInteger("bench.h", 480);

        Thread mon = new Thread(new Runnable() {
            public void run() {
                try {
                    Field fps = field("ao"), dbg = field("C"), screen = field("m");
                    ave mc;
                    while ((mc = ave.A()) == null || screen.get(mc) == null) Thread.sleep(50);
                    log("TITLE_SCREEN reached (" + screen.get(mc).getClass().getName() + ")");
                    Thread.sleep(3000);
                    final ave fmc = mc;
                    mc.a(new Runnable() {
                        public void run() {
                            lockWindow();
                            long seed = Long.getLong("bench.seed", 1234567890L);
                            String dir = "bench-" + System.nanoTime();
                            log("CREATE_WORLD start seed=" + seed + " dir=" + dir);
                            tCreateStart = System.nanoTime();
                            fmc.a(dir, "Bench", new adp(seed, adp.a(0), true, false, adr.a("default")));
                            tCreateEnd = System.nanoTime();
                            log(String.format("CREATE_WORLD returned after %.3f s", (tCreateEnd - tCreateStart) / 1e9));
                        }
                    });
                    while (tCreateEnd == 0) Thread.sleep(50);
                    long tIn = 0;
                    Map<String, Long> cpu0 = null;
                    Runtime rt = Runtime.getRuntime();
                    for (int i = 0; ; i++) {
                        Thread.sleep(1000);
                        boolean inWorld = mc.f != null && mc.h != null;
                        if (inWorld && tIn == 0) { tIn = System.nanoTime(); cpu0 = ThreadCpu.snapshot(); log("IN_WORLD"); }
                        if (inWorld && !(screen.get(mc) instanceof LockScreen)) {
                            final LockScreen ls = new LockScreen();
                            mc.a(new Runnable() { public void run() { fmc.a(ls); } });
                        }
                        mc.a(new Runnable() { public void run() { lockWindow(); } });
                        String d = String.valueOf(dbg.get(mc));
                        java.util.regex.Matcher cm = java.util.regex.Pattern.compile("(\\d+) chunk update").matcher(d);
                        int cu = cm.find() ? Integer.parseInt(cm.group(1)) : -1;
                        log(String.format("SAMPLE fps=%d debug=\"%d fps, %d chunk updates\" inWorld=%b heapMB=%d win=%dx%d pos=%s",
                                fps.getInt(null), fps.getInt(null), cu, inWorld, (rt.totalMemory() - rt.freeMemory()) >> 20,
                                mc.d, mc.e, mc.h == null ? "-" : String.format("%.1f,%.1f,%.1f", mc.h.s, mc.h.t, mc.h.u)));
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
        net.minecraft.client.main.Main.main(args);
    }
}
