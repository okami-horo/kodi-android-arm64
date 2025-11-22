package org.xbmc.kodi.danmaku.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.xbmc.kodi.Main;
import org.xbmc.kodi.R;
import org.xbmc.kodi.XBMCJsonRPC;
import org.xbmc.kodi.danmaku.DanmakuEngine;
import org.xbmc.kodi.danmaku.DanmakuPreferences;
import org.xbmc.kodi.danmaku.api.DanmakuApi;
import org.xbmc.kodi.danmaku.bridge.PlayerEventBridge;
import org.xbmc.kodi.danmaku.clock.MediaSessionClock;
import org.xbmc.kodi.danmaku.dev.DeveloperDanmakuInjector;
import org.xbmc.kodi.danmaku.ipc.DanmakuIpcServer;
import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.MediaKey;
import org.xbmc.kodi.danmaku.source.local.BiliXmlParser;
import org.xbmc.kodi.danmaku.ui.ManualFilePicker;
import org.xbmc.kodi.danmaku.ui.OverlayMountController;
import org.xbmc.kodi.danmaku.ui.TrackSelectionDialog;
import org.xbmc.kodi.danmaku.ui.OsdActions;

import java.io.File;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the dfmExperimental overlay lifecycle and bridges player/MediaSession events.
 */
public final class DanmakuController implements TrackSelectionDialog.Listener, OsdActions.Listener {
    private static final String TAG = "DanmakuController";
    private static final long MEDIA_REFRESH_INTERVAL_MS = 2_000L;
    private static final int REQUEST_MANUAL_FILE = 0xDA01;
    private static final String ACTIVE_PLAYERS_JSON =
            "{\"jsonrpc\":\"2.0\",\"method\":\"Player.GetActivePlayers\",\"id\":\"danmaku-active\"}";
    private static final String ITEM_TEMPLATE =
            "{\"jsonrpc\":\"2.0\",\"method\":\"Player.GetItem\",\"params\":{\"playerid\":%d,\"properties\":[\"file\",\"title\",\"label\"]},\"id\":\"danmaku-item\"}";

    private final Main activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final XBMCJsonRPC jsonRpc;
    private final DanmakuPreferences preferences;
    private final SharedPreferences settingsPrefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
    private final MediaSessionClock mediaClock = new MediaSessionClock();
    private final DanmakuEngine engine;
    private final BiliXmlParser biliXmlParser;
    private final OverlayMountController overlayController;
    private final DanmakuApi api;
    private final ManualFilePicker manualFilePicker = new ManualFilePicker();
    private final TrackSelectionDialog trackDialog;
    private final DeveloperDanmakuInjector developerInjector;
    private final PlayerEventBridge playerBridge;
    private final OsdActions osdActions;
    private final DanmakuIpcServer ipcServer;

    private ViewGroup container;
    private View controlsView;
    private TextView mediaLabel;
    private ImageButton toggleButton;
    private ImageButton selectButton;
    private ImageButton injectButton;
    private ImageButton settingsButton;

    @Nullable
    private MediaDescriptor activeMedia;
    private long lastMediaRefreshMs;
    private boolean scanInFlight;
    private boolean destroyed;
    private boolean danmakuEnabled = true;
    private Menu osdMenu;
    private MenuItem toggleMenuItem;
    private MenuItem selectMenuItem;
    private MenuItem injectMenuItem;
    private MenuItem settingsMenuItem;

    private DanmakuController(Main activity) {
        this.activity = activity;
        this.jsonRpc = new XBMCJsonRPC(activity.getApplicationContext());
        this.preferences = new DanmakuPreferences(activity);
        this.settingsPrefs = DanmakuSettingsStore.prefs(activity);
        DanmakuSettingsStore.ensureDefaults(settingsPrefs);
        this.prefsListener = (prefs, key) -> applySettingsFromPreferences();
        DanmakuOverlayViewHolder overlayViewHolder = new DanmakuOverlayViewHolder(activity);
        this.biliXmlParser = new BiliXmlParser();
        this.engine = new DanmakuEngine(
                overlayViewHolder.view,
                mediaClock,
                preferences,
                biliXmlParser);
        this.overlayController = new OverlayMountController(activity, engine);
        this.api = new DanmakuApi(engine);
        this.trackDialog = new TrackSelectionDialog(activity, api, this);
        this.developerInjector = new DeveloperDanmakuInjector(engine);
        this.playerBridge = new PlayerEventBridge(mediaClock, engine);
        this.osdActions = new OsdActions(this);
        this.ipcServer = new DanmakuIpcServer(activity.getApplicationContext(), engine, biliXmlParser, mainHandler);
        ipcServer.start();
        settingsPrefs.registerOnSharedPreferenceChangeListener(prefsListener);
        applySettingsFromPreferences();
    }

    public static DanmakuController attach(Main activity) {
        return new DanmakuController(activity);
    }

    public void onActivityCreate(ViewGroup videoContainer) {
        this.container = videoContainer;
        overlayController.ensureAttached(videoContainer, null);
        attachControlsOverlay(videoContainer);
    }

    public void onResume() {
        overlayController.onResume();
        destroyed = false;
        scheduleMediaScan(true);
    }

    public void onPause() {
        overlayController.onPause();
    }

    public void onDestroy() {
        destroyed = true;
        overlayController.release();
        engine.detachRenderer();
        ipcServer.close();
        ioExecutor.shutdownNow();
        settingsPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
    }

    public void onConfigurationChanged() {
        if (container != null) {
            overlayController.onConfigurationChanged(container, null);
        }
    }

    public void onFrame() {
        engine.tick();
        scheduleMediaScan(false);
    }

    public void onPlaybackStateChanged(PlaybackState state) {
        playerBridge.onPlaybackStateChanged(state);
    }

    public void onSeek(long positionMs) {
        playerBridge.onSeek(positionMs);
    }

    public void onVisibleBehindCanceled() {
        engine.pause();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CAPTIONS) {
            toggleVisibility();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            showTrackDialog();
            return true;
        }
        return false;
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_MANUAL_FILE) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            showToast(R.string.danmaku_manual_pick_failure);
            return true;
        }
        if (activeMedia == null) {
            showToast(R.string.danmaku_no_media_for_tracks);
            return true;
        }
        Uri uri = data.getData();
        if (uri == null) {
            showToast(R.string.danmaku_manual_pick_failure);
            return true;
        }
        try {
            final int flags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            activity.getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (SecurityException ignored) {
        }
        boolean accepted = manualFilePicker.selectDocument(activity, uri, activeMedia.mediaKey, engine);
        if (accepted) {
            showToast(R.string.danmaku_manual_pick_success);
        } else {
            showToast(R.string.danmaku_manual_pick_failure);
        }
        return true;
    }

    @Override
    public void onManualPickRequested() {
        startManualPicker();
    }

    @Override
    public void onToggleVisibility() {
        toggleVisibility();
    }

    @Override
    public void onSelectTrack() {
        showTrackDialog();
    }

    @Override
    public void onInjectSample() {
        developerInjector.inject(activeMediaKeyOrFallback());
    }

    @Override
    public void onOpenSettings() {
        openSettings();
    }

    private void attachControlsOverlay(ViewGroup videoContainer) {
        if (!(videoContainer instanceof RelativeLayout)) {
            Log.w(TAG, "Video container is not a RelativeLayout; danmaku controls may overlap");
        }
        if (controlsView != null) {
            videoContainer.removeView(controlsView);
        }
        controlsView = LayoutInflater.from(videoContainer.getContext())
                .inflate(R.layout.danmaku_controls_overlay, videoContainer, false);
        mediaLabel = controlsView.findViewById(R.id.text_media_label);
        toggleButton = controlsView.findViewById(R.id.button_toggle_danmaku);
        selectButton = controlsView.findViewById(R.id.button_select_danmaku);
        injectButton = controlsView.findViewById(R.id.button_inject_danmaku);
        settingsButton = controlsView.findViewById(R.id.button_danmaku_settings);
        toggleButton.setOnClickListener(v -> toggleVisibility());
        selectButton.setOnClickListener(v -> showTrackDialog());
        injectButton.setOnClickListener(v -> developerInjector.inject(activeMediaKeyOrFallback()));
        settingsButton.setOnClickListener(v -> openSettings());
        selectButton.setEnabled(false);
        updateToggleIcon(danmakuEnabled);
        controlsView.setVisibility(View.GONE);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_END);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        int margin = controlsView.getResources().getDimensionPixelSize(R.dimen.danmaku_controls_margin);
        params.setMargins(margin, margin, margin, margin);
        videoContainer.addView(controlsView, params);
    }

    private void applySettingsFromPreferences() {
        DanmakuConfig config = DanmakuSettingsStore.buildConfig(settingsPrefs);
        preferences.saveDefaultConfig(config);
        engine.updateConfig(config);
        danmakuEnabled = settingsPrefs.getBoolean(DanmakuSettingsStore.KEY_ENABLED, true);
        engine.setVisibility(danmakuEnabled);
        updateToggleIcon(danmakuEnabled);
    }

    public void onCreateOptionsMenu(Menu menu) {
        this.osdMenu = menu;
        toggleMenuItem = menu.findItem(R.id.action_toggle_danmaku);
        selectMenuItem = menu.findItem(R.id.action_select_danmaku_track);
        injectMenuItem = menu.findItem(R.id.action_inject_danmaku);
        settingsMenuItem = menu.findItem(R.id.action_open_danmaku_settings);
        updateToggleIcon(danmakuEnabled);
        updateMenuItemsForMedia();
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        return osdActions.onMenuItemSelected(item);
    }

    private void toggleVisibility() {
        boolean newState = !settingsPrefs.getBoolean(DanmakuSettingsStore.KEY_ENABLED, true);
        settingsPrefs.edit().putBoolean(DanmakuSettingsStore.KEY_ENABLED, newState).apply();
    }

    private void showTrackDialog() {
        if (activeMedia == null) {
            showToast(R.string.danmaku_no_media_for_tracks);
            return;
        }
        trackDialog.show(activeMedia.mediaKey);
    }

    private void openSettings() {
        DanmakuSettingsActivity.launch(activity);
    }

    private void startManualPicker() {
        if (activeMedia == null) {
            showToast(R.string.danmaku_no_media_for_tracks);
            return;
        }
        Intent intent = manualFilePicker.buildOpenDocumentIntent();
        try {
            activity.startActivityForResult(intent, REQUEST_MANUAL_FILE);
        } catch (Exception ex) {
            Log.w(TAG, "Unable to launch document picker", ex);
            showToast(R.string.danmaku_manual_pick_failure);
        }
    }

    private void scheduleMediaScan(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && (scanInFlight || (now - lastMediaRefreshMs) < MEDIA_REFRESH_INTERVAL_MS)) {
            return;
        }
        scanInFlight = true;
        ioExecutor.execute(() -> {
            MediaDescriptor descriptor = queryActiveMedia();
            mainHandler.post(() -> {
                scanInFlight = false;
                applyActiveMedia(descriptor);
            });
        });
        lastMediaRefreshMs = now;
    }

    @Nullable
    private MediaDescriptor queryActiveMedia() {
        try {
            JsonArray players = jsonRpc.request_array(ACTIVE_PLAYERS_JSON);
            if (players == null) {
                return null;
            }
            for (JsonElement element : players) {
                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("type") || !"video".equals(obj.get("type").getAsString())) {
                    continue;
                }
                int playerId = obj.get("playerid").getAsInt();
                String payload = String.format(Locale.US, ITEM_TEMPLATE, playerId);
                JsonObject itemResponse = jsonRpc.request_object(payload);
                if (itemResponse == null || !itemResponse.has("result")) {
                    continue;
                }
                JsonObject result = itemResponse.getAsJsonObject("result");
                if (result == null || !result.has("item")) {
                    continue;
                }
                JsonObject item = result.getAsJsonObject("item");
                String file = item.has("file") ? item.get("file").getAsString() : null;
                if (TextUtils.isEmpty(file)) {
                    continue;
                }
                String label = item.has("label") ? item.get("label").getAsString() : item.get("title") != null
                        ? item.get("title").getAsString() : file;
                return buildDescriptor(file, label);
            }
        } catch (RuntimeException ex) {
            Log.w(TAG, "Player metadata query failed", ex);
        }
        return null;
    }

    private MediaDescriptor buildDescriptor(String filePath, String label) {
        String resolvedLocalPath = resolveLocalPath(filePath);
        File local = resolvedLocalPath == null ? null : new File(resolvedLocalPath);
        long size = local != null && local.exists() ? local.length() : 0L;
        long mtime = local != null && local.exists() ? local.lastModified() : 0L;
        String keyPath = resolvedLocalPath != null ? resolvedLocalPath : filePath;
        MediaKey mediaKey = new MediaKey(keyPath, size, mtime);
        return new MediaDescriptor(mediaKey, label, filePath);
    }

    private void applyActiveMedia(@Nullable MediaDescriptor descriptor) {
        if (destroyed) {
            return;
        }
        if (Objects.equals(activeMedia, descriptor)) {
            return;
        }
        activeMedia = descriptor;
        if (descriptor == null) {
            if (controlsView != null) {
                controlsView.setVisibility(View.GONE);
            }
            if (selectButton != null) {
                selectButton.setEnabled(false);
            }
            updateMenuItemsForMedia();
            return;
        }
        overlayController.ensureAttached(container, null);
        controlsView.setVisibility(View.VISIBLE);
        if (selectButton != null) {
            selectButton.setEnabled(true);
        }
        updateMediaLabel(descriptor);
        engine.setVisibility(danmakuEnabled);
        updateToggleIcon(danmakuEnabled);
        updateMenuItemsForMedia();
        engine.getTrackCandidates(descriptor.mediaKey);
    }

    private void updateMediaLabel(MediaDescriptor descriptor) {
        if (mediaLabel == null) {
            return;
        }
        mediaLabel.setText(descriptor.label);
    }

    private void updateToggleIcon(boolean visible) {
        if (toggleButton == null) {
            return;
        }
        toggleButton.setImageResource(visible
                ? android.R.drawable.ic_menu_view
                : android.R.drawable.ic_menu_close_clear_cancel);
        if (toggleMenuItem != null) {
            toggleMenuItem.setChecked(visible);
        }
    }

    private void updateMenuItemsForMedia() {
        boolean hasMedia = activeMedia != null;
        if (selectMenuItem != null) {
            selectMenuItem.setEnabled(hasMedia);
        }
        if (injectMenuItem != null) {
            injectMenuItem.setEnabled(true);
        }
        if (settingsMenuItem != null) {
            settingsMenuItem.setEnabled(true);
        }
    }

    private MediaKey activeMediaKeyOrFallback() {
        if (activeMedia != null) {
            return activeMedia.mediaKey;
        }
        return new MediaKey("dev://manual", 0L, 0L);
    }

    private void showToast(int resId) {
        Toast.makeText(activity, resId, Toast.LENGTH_SHORT).show();
    }

    private static String resolveLocalPath(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("file://")) {
            return Uri.parse(path).getPath();
        }
        if (path.startsWith("/")) {
            return path;
        }
        return null;
    }

    private static final class DanmakuOverlayViewHolder {
        final org.xbmc.kodi.danmaku.DanmakuOverlayView view;

        DanmakuOverlayViewHolder(Activity activity) {
            this.view = new org.xbmc.kodi.danmaku.DanmakuOverlayView(activity);
        }
    }

    private static final class MediaDescriptor {
        final MediaKey mediaKey;
        final String label;
        final String sourcePath;

        MediaDescriptor(MediaKey mediaKey, String label, String sourcePath) {
            this.mediaKey = mediaKey;
            this.label = label;
            this.sourcePath = sourcePath;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MediaDescriptor)) return false;
            MediaDescriptor other = (MediaDescriptor) obj;
            return Objects.equals(mediaKey, other.mediaKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mediaKey);
        }
    }
}
