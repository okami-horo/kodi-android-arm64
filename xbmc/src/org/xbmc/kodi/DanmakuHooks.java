package org.xbmc.kodi;

import android.content.Intent;
import android.content.res.Configuration;
import android.media.session.PlaybackState;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reflection-based bridge that loads the dfmExperimental controller at runtime only
 * when the flavor enables danmaku.
 */
final class DanmakuHooks {
    private static final String TAG = "DanmakuHooks";
    private static final String CONTROLLER_CLASS = "org.xbmc.kodi.danmaku.app.DanmakuController";

    @Nullable
    private static DanmakuHooks instance;

    private final Object controller;
    private final Method onActivityCreate;
    private final Method onResume;
    private final Method onPause;
    private final Method onDestroy;
    private final Method onFrame;
    private final Method onConfigurationChanged;
    private final Method onPlaybackStateChanged;
    private final Method onSeek;
    private final Method onVisibleBehindCanceled;
    private final Method onKeyDown;
    private final Method onActivityResult;
    private final Method onCreateOptionsMenu;
    private final Method onOptionsItemSelected;

    private DanmakuHooks(Object controller) throws NoSuchMethodException {
        this.controller = controller;
        Class<?> clazz = controller.getClass();
        onActivityCreate = clazz.getMethod("onActivityCreate", ViewGroup.class);
        onResume = clazz.getMethod("onResume");
        onPause = clazz.getMethod("onPause");
        onDestroy = clazz.getMethod("onDestroy");
        onFrame = clazz.getMethod("onFrame");
        onConfigurationChanged = clazz.getMethod("onConfigurationChanged");
        onPlaybackStateChanged = clazz.getMethod("onPlaybackStateChanged", PlaybackState.class);
        onSeek = clazz.getMethod("onSeek", long.class);
        onVisibleBehindCanceled = clazz.getMethod("onVisibleBehindCanceled");
        onKeyDown = clazz.getMethod("onKeyDown", int.class, KeyEvent.class);
        onActivityResult = clazz.getMethod("onActivityResult", int.class, int.class, Intent.class);
        onCreateOptionsMenu = clazz.getMethod("onCreateOptionsMenu", Menu.class);
        onOptionsItemSelected = clazz.getMethod("onOptionsItemSelected", MenuItem.class);
    }

    @Nullable
    static DanmakuHooks attach(Main activity) {
        if (!BuildConfig.DANMAKU_ENABLED) {
            return null;
        }
        try {
            Class<?> controllerClass = Class.forName(CONTROLLER_CLASS);
            Method attachMethod = controllerClass.getMethod("attach", Main.class);
            Object controller = attachMethod.invoke(null, activity);
            DanmakuHooks hooks = new DanmakuHooks(controller);
            instance = hooks;
            return hooks;
        } catch (Throwable t) {
            Log.w(TAG, "Unable to initialize danmaku controller", t);
            instance = null;
            return null;
        }
    }

    static void dispatchPlaybackState(PlaybackState state) {
        DanmakuHooks hooks = instance;
        if (hooks != null) {
            hooks.invoke(hooks.onPlaybackStateChanged, state);
        }
    }

    static void dispatchSeek(long positionMs) {
        DanmakuHooks hooks = instance;
        if (hooks != null) {
            hooks.invoke(hooks.onSeek, positionMs);
        }
    }

    static void dispatchVisibleBehindCanceled() {
        DanmakuHooks hooks = instance;
        if (hooks != null) {
            hooks.invoke(hooks.onVisibleBehindCanceled);
        }
    }

    static void onCreateOptionsMenu(Menu menu) {
        DanmakuHooks hooks = instance;
        if (hooks != null) {
            hooks.invoke(hooks.onCreateOptionsMenu, menu);
        }
    }

    static boolean onOptionsItemSelected(MenuItem item) {
        DanmakuHooks hooks = instance;
        if (hooks != null) {
            Object result = hooks.invokeWithResult(hooks.onOptionsItemSelected, item);
            return result instanceof Boolean && (Boolean) result;
        }
        return false;
    }

    void onActivityCreate(ViewGroup container) {
        invoke(onActivityCreate, container);
    }

    void onResume() {
        invoke(onResume);
    }

    void onPause() {
        invoke(onPause);
    }

    void onDestroy() {
        invoke(onDestroy);
        instance = null;
    }

    void onFrame() {
        invoke(onFrame);
    }

    void onConfigurationChanged(Configuration newConfig) {
        invoke(onConfigurationChanged);
    }

    boolean onKeyDown(int keyCode, KeyEvent event) {
        Object result = invokeWithResult(onKeyDown, keyCode, event);
        return result instanceof Boolean && (Boolean) result;
    }

    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        Object result = invokeWithResult(onActivityResult, requestCode, resultCode, data);
        return result instanceof Boolean && (Boolean) result;
    }

    private void invoke(Method method, Object... args) {
        try {
            method.invoke(controller, args);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            Log.w(TAG, "Danmaku invocation failed: " + method.getName(), ex);
        }
    }

    @Nullable
    private Object invokeWithResult(Method method, Object... args) {
        try {
            return method.invoke(controller, args);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            Log.w(TAG, "Danmaku invocation failed: " + method.getName(), ex);
            return null;
        }
    }
}
