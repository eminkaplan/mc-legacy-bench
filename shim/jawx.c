/* Forwards LWJGL 2's JAWT_GetAWT@SUNWprivate_1.1 (Java 8 ABI) to the libjawt.so of a modern JDK. */
#include <dlfcn.h>
#include <stdio.h>
typedef unsigned char (*get_awt_fn)(void *env, void *awt);
unsigned char JAWT_GetAWT(void *env, void *awt) {
    static get_awt_fn real;
    if (!real) {
        void *h = dlopen("libjawt.so", RTLD_NOW | RTLD_NOLOAD);
        if (!h) h = dlopen("libjawt.so", RTLD_NOW);
        real = h ? (get_awt_fn) dlsym(h, "JAWT_GetAWT") : 0;
        if (!real) { fprintf(stderr, "jawx: real JAWT_GetAWT not found: %s\n", dlerror()); return 0; }
    }
    return real(env, awt);
}
