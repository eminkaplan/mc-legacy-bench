package bench;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Reflection by Mojang's official names. Reads the client_mappings.txt shipped since 1.14.4
// (-Dbench.mappings=<file>); without one (unobfuscated clients such as 26.x) every name maps to itself.
//   m.call(mc, "net.minecraft.client.Minecraft", "getWindow()")
//   m.get(null, "net.minecraft.world.level.GameType", "SURVIVAL")
//   m.make("net.minecraft.world.level.GameRules", "()")
public class Mapped {
    private final Map<String, String> classes = new HashMap<String, String>();
    private final Map<String, String> fields = new HashMap<String, String>();   // "owner#name" -> obf
    private final Map<String, String> methods = new HashMap<String, String>();  // "owner#name(params)" -> obf

    public static Mapped load() throws Exception {
        Mapped m = new Mapped();
        String path = System.getProperty("bench.mappings");
        if (path == null || path.isEmpty()) return m;
        BufferedReader r = new BufferedReader(new FileReader(path));
        String owner = null, line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("#") || line.isEmpty()) continue;
            if (!line.startsWith(" ")) {                       // "named.Class -> obf:"
                String[] p = line.substring(0, line.length() - 1).split(" -> ");
                owner = p[0];
                m.classes.put(p[0], p[1]);
                continue;
            }
            String[] p = line.trim().split(" -> ");            // "[a:b:]type name[(params)] -> obf"
            String decl = p[0].replaceFirst("^\\d+:\\d+:", "");
            String name = decl.substring(decl.indexOf(' ') + 1);
            if (name.contains("(")) m.methods.put(owner + "#" + name, p[1]);
            else m.fields.put(owner + "#" + name, p[1]);
        }
        r.close();
        return m;
    }

    public Class<?> cls(String named) throws ClassNotFoundException {
        if (named.endsWith("[]")) return java.lang.reflect.Array.newInstance(cls(named.substring(0, named.length() - 2)), 0).getClass();
        switch (named) {
            case "int": return int.class;
            case "long": return long.class;
            case "boolean": return boolean.class;
            case "float": return float.class;
            case "double": return double.class;
            case "byte": return byte.class;
            case "short": return short.class;
            case "char": return char.class;
            default:
                String obf = classes.containsKey(named) ? classes.get(named) : named;
                return Class.forName(obf, false, ClassLoader.getSystemClassLoader());
        }
    }

    private Class<?>[] params(String sig) throws ClassNotFoundException {
        String inner = sig.substring(sig.indexOf('(') + 1, sig.lastIndexOf(')')).trim();
        if (inner.isEmpty()) return new Class<?>[0];
        String[] names = inner.split(",");
        Class<?>[] out = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) out[i] = cls(names[i].trim());
        return out;
    }

    public Object get(Object target, String owner, String field) throws Exception {
        return field(owner, field).get(target);
    }

    public void set(Object target, String owner, String field, Object value) throws Exception {
        field(owner, field).set(target, value);
    }

    private Field field(String owner, String name) throws Exception {
        String obf = fields.containsKey(owner + "#" + name) ? fields.get(owner + "#" + name) : name;
        for (Class<?> c = cls(owner); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(obf);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(owner + "." + name + " (" + obf + ")");
    }

    // sig = "name(named.param.Type,...)" exactly as in the mappings; owner = the class that declares it.
    public Object call(Object target, String owner, String sig, Object... args) throws Exception {
        String obf = methods.containsKey(owner + "#" + sig) ? methods.get(owner + "#" + sig) : sig.substring(0, sig.indexOf('('));
        Class<?>[] ps = params(sig);
        List<Class<?>> todo = new ArrayList<Class<?>>();
        todo.add(target != null ? target.getClass() : cls(owner));
        while (!todo.isEmpty()) {
            Class<?> c = todo.remove(0);
            try {
                Method mt = c.getDeclaredMethod(obf, ps);
                mt.setAccessible(true);
                return mt.invoke(target, args);
            } catch (NoSuchMethodException ignored) {
            }
            if (c.getSuperclass() != null) todo.add(c.getSuperclass());
            for (Class<?> i : c.getInterfaces()) todo.add(i);
        }
        throw new NoSuchMethodException(owner + "." + sig + " (" + obf + ")");
    }

    // paramSig = "(named.Type,...)"
    public Object make(String owner, String paramSig, Object... args) throws Exception {
        Constructor<?> k = cls(owner).getDeclaredConstructor(params(paramSig));
        k.setAccessible(true);
        return k.newInstance(args);
    }
}
