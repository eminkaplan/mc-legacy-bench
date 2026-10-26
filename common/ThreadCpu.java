import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Per-thread CPU time from /proc/self/task (Linux), grouped by thread name with trailing numbers stripped.
public class ThreadCpu {
    static final double HZ = 100.0;

    public static Map<String, Long> snapshot() {
        Map<String, Long> m = new HashMap<String, Long>();
        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks == null) return m;
        for (File t : tasks) {
            try {
                String comm = new String(Files.readAllBytes(new File(t, "comm").toPath())).trim();
                String stat = new String(Files.readAllBytes(new File(t, "stat").toPath()));
                String[] f = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
                long ticks = Long.parseLong(f[11]) + Long.parseLong(f[12]); // utime + stime
                m.put(t.getName() + "|" + comm, ticks);
            } catch (Exception ignored) {
            }
        }
        return m;
    }

    public static String report(Map<String, Long> before, Map<String, Long> after, double wallSecs) {
        Map<String, Long> byName = new HashMap<String, Long>();
        Map<String, Integer> count = new HashMap<String, Integer>();
        long total = 0;
        for (Map.Entry<String, Long> e : after.entrySet()) {
            Long b = before.get(e.getKey());
            long d = Math.max(0, e.getValue() - (b == null ? 0 : b));
            String name = e.getKey().substring(e.getKey().indexOf('|') + 1).replaceAll("[\\s#-]*\\d+$", "");
            byName.put(name, (byName.containsKey(name) ? byName.get(name) : 0) + d);
            count.put(name, (count.containsKey(name) ? count.get(name) : 0) + 1);
            total += d;
        }
        List<Map.Entry<String, Long>> l = new ArrayList<Map.Entry<String, Long>>(byName.entrySet());
        Collections.sort(l, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder(String.format("THREADS total=%.1f cores avg over %.0fs", total / HZ / wallSecs, wallSecs));
        for (int i = 0; i < Math.min(8, l.size()); i++) {
            Map.Entry<String, Long> e = l.get(i);
            sb.append(String.format("%n  %-18s x%-2d %5.1f%% of one core", e.getKey(), count.get(e.getKey()), 100.0 * e.getValue() / HZ / wallSecs));
        }
        return sb.toString();
    }
}
