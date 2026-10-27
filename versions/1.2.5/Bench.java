import bench.ThreadCpu;
import java.io.File;
import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

// Headless benchmark harness for Minecraft 1.2.5 (obfuscated names, found with javap; there is no MCP mapping here):
//   Minecraft.a (static)     the instance (no getMinecraft() yet)     Minecraft.aj (static) : minecraftDir
//   Minecraft.a(S,S,fj)   startWorld: fj = WorldSettings(seed, gameType, mapFeatures, hardcore, vx WorldType)
//   Minecraft.M              debug string "N fps, M chunk updates" (no separate FPS counter)
//   Minecraft.s : vp         currentScreen          Minecraft.f / .h   theWorld / thePlayer
//   vp.a()                  GuiScreen.updateScreen, called from runTick on the main thread
//   vp.b()                  doesGuiPauseGame        vp.a(char,int) keyTyped
//   Entity nn.o/p/q           posX / posY / posZ
//   Minecraft.c = new aes(mc)   PlayerControllerSP (survival)
// Before 1.3 there is no integrated server: the world is generated inside the client, on the main thread.
public class Bench {
    static volatile long tCreateStart, tCreateEnd;
    static final long T0 = System.nanoTime();
    static int W, H;

    static double sec(long t) { return (t - T0) / 1e9; }
    static void log(String s) { System.out.printf("[BENCH %8.3f] %s%n", sec(System.nanoTime()), s); System.out.flush(); }

    // Runs on the main game thread on the first tick it is displayed.
    public static class CreateWorldScreen extends vp {
        private boolean fired;
        public void a() {
            if (fired) return;
            fired = true;
            long seed = Long.getLong("bench.seed", 1234567890L);
            String dir = "bench-" + System.nanoTime();
            log("CREATE_WORLD start seed=" + seed + " dir=" + dir);
            tCreateStart = System.nanoTime();
            Minecraft mc = minecraft();
            mc.c = new aes(mc);   // the survival PlayerController, as GuiCreateWorld sets it before startWorld
            mc.a(dir, "Bench", new fj(seed, 0, true, false, vx.a("default")));
            tCreateEnd = System.nanoTime();
            log(String.format("CREATE_WORLD returned after %.3f s", (tCreateEnd - tCreateStart) / 1e9));
        }
    }

    // Shown over the world once loaded: does not pause the game and swallows all keys,
    // so stray keyboard/mouse input cannot move the player or toggle F3.
    public static class LockScreen extends vp {
        public boolean b() { return false; }
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

    static Minecraft minecraft() {
        try {
            return (Minecraft) field("a").get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        File gameDir = new File(System.getProperty("bench.gamedir")).getAbsoluteFile();
        field("aj").set(null, gameDir);
        final int measureSecs = Integer.getInteger("bench.seconds", 60);
        W = Integer.getInteger("bench.w", 854);
        H = Integer.getInteger("bench.h", 480);

        Thread mon = new Thread(new Runnable() {
            public void run() {
                try {
                    Field dbg = field("M"), screen = field("s");
                    Pattern fpsRx = Pattern.compile("^(\\d+) fps");
                    Minecraft mc;
                    while ((mc = minecraft()) == null || screen.get(mc) == null) Thread.sleep(50);
                    log("TITLE_SCREEN reached (" + screen.get(mc).getClass().getName() + ")");
                    Thread.sleep(3000);
                    CreateWorldScreen cw = new CreateWorldScreen();
                    cw.a(mc, mc.d, mc.e);
                    screen.set(mc, cw);
                    lockWindow(mc, W, H);
                    while (tCreateEnd == 0) Thread.sleep(50);
                    long tIn = 0;
                    java.util.Map<String, Long> cpu0 = null;
                    Runtime rt = Runtime.getRuntime();
                    for (int i = 0; ; i++) {
                        Thread.sleep(1000);
                        boolean inWorld = mc.f != null && mc.h != null;
                        if (inWorld && tIn == 0) { tIn = System.nanoTime(); cpu0 = ThreadCpu.snapshot(); log("IN_WORLD"); }
                        if (inWorld && !(screen.get(mc) instanceof LockScreen)) {
                            LockScreen ls = new LockScreen();
                            ls.a(mc, mc.d, mc.e);
                            screen.set(mc, ls);
                        }
                        lockWindow(mc, W, H);
                        String d = String.valueOf(dbg.get(mc));
                        Matcher fm = fpsRx.matcher(d);
                        log(String.format("SAMPLE fps=%d debug=\"%s\" inWorld=%b heapMB=%d win=%dx%d pos=%s",
                                fm.find() ? Integer.parseInt(fm.group(1)) : -1, d, inWorld, (rt.totalMemory() - rt.freeMemory()) >> 20,
                                mc.d, mc.e, mc.h == null ? "-" : String.format("%.1f,%.1f,%.1f", mc.h.o, mc.h.p, mc.h.q)));
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
