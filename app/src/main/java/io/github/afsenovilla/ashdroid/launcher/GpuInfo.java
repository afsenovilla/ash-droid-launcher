package io.github.afsenovilla.ashdroid.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;

/**
 * Which GPU the device has (GL_RENDERER), read once from a tiny off-screen GLES context and
 * cached until the system is updated. Used to work around Mali driver problems with the game's
 * very tall tileset images (glitched maps).
 */
public final class GpuInfo
{
    private static final String PREFS = "ashdroid";
    private static final String KEY_RENDERER = "gpu_renderer";
    private static final String KEY_FOR = "gpu_renderer_for";

    private GpuInfo() {}

    /** GL_RENDERER, e.g. "Mali-G68" or "Adreno (TM) 650"; "" if it could not be read. */
    public static synchronized String renderer(Context ctx)
    {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String build = Build.FINGERPRINT;
        if (build.equals(p.getString(KEY_FOR, null))) {
            return p.getString(KEY_RENDERER, "");
        }
        String r = probe();
        p.edit().putString(KEY_RENDERER, r).putString(KEY_FOR, build).apply();
        return r;
    }

    public static boolean isMali(Context ctx)
    {
        return renderer(ctx).toLowerCase(java.util.Locale.ROOT).contains("mali");
    }

    private static String probe()
    {
        EGLDisplay dpy = EGL14.EGL_NO_DISPLAY;
        EGLContext ctx = EGL14.EGL_NO_CONTEXT;
        EGLSurface surf = EGL14.EGL_NO_SURFACE;
        try {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] ver = new int[2];
            if (!EGL14.eglInitialize(dpy, ver, 0, ver, 1)) return "";
            int[] attrs = {
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE };
            EGLConfig[] cfg = new EGLConfig[1];
            int[] n = new int[1];
            if (!EGL14.eglChooseConfig(dpy, attrs, 0, cfg, 0, 1, n, 0) || n[0] < 1) return "";
            ctx = EGL14.eglCreateContext(dpy, cfg[0], EGL14.EGL_NO_CONTEXT,
                    new int[] { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE }, 0);
            surf = EGL14.eglCreatePbufferSurface(dpy, cfg[0],
                    new int[] { EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE }, 0);
            if (ctx == EGL14.EGL_NO_CONTEXT || surf == EGL14.EGL_NO_SURFACE) return "";
            if (!EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) return "";
            String r = GLES20.glGetString(GLES20.GL_RENDERER);
            return r != null ? r : "";
        } catch (RuntimeException e) {
            return "";
        } finally {
            if (dpy != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, surf);
                if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(dpy, ctx);
                EGL14.eglTerminate(dpy);
            }
        }
    }
}
