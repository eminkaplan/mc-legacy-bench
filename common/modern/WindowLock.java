package bench;

// Pins the game window to a fixed size and cuts keyboard/mouse input, so nothing moves the player during a run.
// Main thread only. Implementations: GlfwLock (1.13 .. 1.21), SdlLock (26.x); picked with -Dbench.windowlock.
public interface WindowLock {
    // Returns the window size after locking: {width, height}.
    int[] lock(long handle, int width, int height);
}
