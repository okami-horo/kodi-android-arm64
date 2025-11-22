import os
import threading

import xbmc
import xbmcaddon
import xbmcvfs

from .api import DandanAPI
from .filehash import compute_hash
from .ipc import DanmakuIPC
from .utils import log, media_key, sanitize_keyword, to_local_path


class PlaybackListener(xbmc.Player):
    def __init__(self, callback):
        super().__init__()
        self._callback = callback

    def _notify(self, playing):
        path = None
        if playing:
            try:
                path = self.getPlayingFile()
            except Exception:  # pylint: disable=broad-except
                path = None
        self._callback(playing, path)

    def onAVStarted(self):
        self._notify(True)

    def onPlayBackStarted(self):
        self._notify(True)

    def onPlayBackResumed(self):
        self._notify(True)

    def onPlayBackEnded(self):
        self._notify(False)

    def onPlayBackStopped(self):
        self._notify(False)


class SettingsMonitor(xbmc.Monitor):
    def __init__(self, callback):
        super().__init__()
        self._callback = callback

    def onSettingsChanged(self):
        self._callback()


class DandanplayService:
    def __init__(self):
        self._api = DandanAPI()
        self._addon = xbmcaddon.Addon()
        self._settings = self._load_settings()
        self._apply_api_credentials()
        self._ipc = DanmakuIPC()
        self._monitor = SettingsMonitor(self._reload_settings)
        self._player = PlaybackListener(self._handle_playback_event)
        self._lock = threading.Lock()
        self._worker = None
        self._current_path = None
        self._active_media_key = None

    def run(self):
        log("Dandanplay service started")
        while not self._monitor.abortRequested():
            if self._monitor.waitForAbort(1):
                break
        log("Dandanplay service stopped")

    def _handle_playback_event(self, playing, path):
        if not playing:
            self._active_media_key = None
            self._ipc.unload()
            return
        if not self._settings["enabled"]:
            return
        if not path:
            return
        with self._lock:
            if path == self._current_path and self._worker and self._worker.is_alive():
                return
            self._current_path = path
            duration = 0
            try:
                duration = int(self._player.getTotalTime())
            except Exception:  # pylint: disable=broad-except
                duration = 0
            self._worker = threading.Thread(
                target=self._process_video, args=(path, duration), daemon=True
            )
            self._worker.start()

    def _process_video(self, file_url, duration):
        local_path = to_local_path(file_url)
        if not local_path or not xbmcvfs.exists(local_path):
            log(f"Unsupported path for danmaku matching: {file_url}")
            return
        stat = xbmcvfs.Stat(local_path)
        size = int(stat.st_size())
        mtime = int(stat.st_mtime())
        hash_value = compute_hash(local_path)
        episode = None
        if hash_value:
            episode = self._api.match_episode(
                hash_value, os.path.basename(local_path), size, duration
            )
        if not episode and self._settings["fallback"]:
            keyword = sanitize_keyword(local_path)
            if keyword:
                episode = self._api.search_episode(keyword)
        if not episode:
            log("No danmaku match found for current media", xbmc.LOGINFO)
            return
        comments = self._api.download_comments(episode.get("episodeId"))
        if not comments:
            log("Dandanplay returned empty comments")
            return
        xml_bytes = self._api.build_bili_xml(comments)
        key = media_key(local_path, size, mtime)
        track_id = f"dandanplay-{episode.get('episodeId')}"
        title = episode.get("animeTitle") or "Dandanplay"
        episode_title = episode.get("episodeTitle")
        if episode_title:
            title = f"{title} - {episode_title}"
        prefs = {}
        if self._settings["offset_ms"]:
            prefs["offsetMs"] = self._settings["offset_ms"]
        success = self._ipc.load_track(key, track_id, title, xml_bytes, prefs or None)
        if success:
            self._active_media_key = key
            log(f"Bound danmaku track {track_id} from Dandanplay", xbmc.LOGINFO)
        else:
            log("Failed to bind danmaku track via IPC", xbmc.LOGWARNING)

    def _load_settings(self):
        enabled = self._get_bool("enable_service")
        fallback = self._get_bool("enable_search_fallback")
        try:
            offset_ms = self._addon.getSettingInt("offset_ms")
        except AttributeError:
            offset_ms = int(float(self._addon.getSetting("offset_ms") or 0))
        app_id = self._addon.getSetting("app_id")
        app_secret = self._addon.getSetting("app_secret")
        return {
            "enabled": enabled,
            "fallback": fallback,
            "offset_ms": offset_ms,
            "app_id": app_id.strip(),
            "app_secret": app_secret.strip(),
        }

    def _reload_settings(self):
        self._settings = self._load_settings()
        self._apply_api_credentials()

    def _get_bool(self, key):
        try:
            return self._addon.getSettingBool(key)
        except AttributeError:
            return self._addon.getSetting(key).lower() == "true"

    def _apply_api_credentials(self):
        self._api.set_credentials(
            self._settings.get("app_id"),
            self._settings.get("app_secret"),
        )
