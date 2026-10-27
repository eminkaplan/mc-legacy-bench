package bench;

import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLVideo;

// SDL3 has no per-window callbacks to drop: input events are disabled globally instead.
public class SdlLock implements WindowLock {
    private static final int[] INPUT_EVENTS = {
        SDLEvents.SDL_EVENT_KEY_DOWN, SDLEvents.SDL_EVENT_KEY_UP, SDLEvents.SDL_EVENT_TEXT_INPUT,
        SDLEvents.SDL_EVENT_MOUSE_MOTION, SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN, SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP,
        SDLEvents.SDL_EVENT_MOUSE_WHEEL, SDLEvents.SDL_EVENT_DROP_FILE };

    public int[] lock(long h, int width, int height) {
        if ((SDLVideo.SDL_GetWindowFlags(h) & SDLVideo.SDL_WINDOW_MAXIMIZED) != 0) SDLVideo.SDL_RestoreWindow(h);
        SDLVideo.SDL_SetWindowResizable(h, false);
        SDLVideo.SDL_SetWindowMinimumSize(h, width, height);
        SDLVideo.SDL_SetWindowMaximumSize(h, width, height);
        IntBuffer w = BufferUtils.createIntBuffer(1), hh = BufferUtils.createIntBuffer(1);
        SDLVideo.SDL_GetWindowSize(h, w, hh);
        if (w.get(0) != width || hh.get(0) != height) SDLVideo.SDL_SetWindowSize(h, width, height);
        for (int type : INPUT_EVENTS) SDLEvents.SDL_SetEventEnabled(type, false);
        return new int[] { w.get(0), hh.get(0) };
    }
}
