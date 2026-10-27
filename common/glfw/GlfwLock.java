package bench;

import org.lwjgl.glfw.GLFW;

public class GlfwLock implements WindowLock {
    public int[] lock(long h, int width, int height) {
        if (GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) GLFW.glfwRestoreWindow(h);
        GLFW.glfwSetWindowAttrib(h, GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowSizeLimits(h, width, height, width, height);
        int[] w = new int[1], hh = new int[1];
        GLFW.glfwGetWindowSize(h, w, hh);
        if (w[0] != width || hh[0] != height) GLFW.glfwSetWindowSize(h, width, height);
        GLFW.glfwSetKeyCallback(h, null);
        GLFW.glfwSetCharCallback(h, null);
        GLFW.glfwSetCharModsCallback(h, null);
        GLFW.glfwSetMouseButtonCallback(h, null);
        GLFW.glfwSetCursorPosCallback(h, null);
        GLFW.glfwSetScrollCallback(h, null);
        GLFW.glfwSetDropCallback(h, null);
        return new int[] { w[0], hh[0] };
    }
}
