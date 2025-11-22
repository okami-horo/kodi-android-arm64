import base64
import gzip
import json
import time
from urllib import error, request

import xbmc
import xbmcvfs

from .utils import log, translate_path


class DanmakuIPC:
    def __init__(self, retry_seconds=10):
        self._retry_seconds = retry_seconds
        self._token_path = translate_path(
            "special://profile/addon_data/script.service.dandanplay/ipc_token.json"
        )
        self._token = None
        self._port = None
        self._token_timestamp = 0

    def load_track(self, media_key, track_id, title, xml_bytes, prefs=None):
        if not self._ensure_token():
            log("IPC token unavailable", xbmc.LOGWARNING)
            return False
        compressed = gzip.compress(xml_bytes)
        payload = base64.b64encode(compressed).decode("ascii")
        body = {
            "mediaKey": media_key,
            "trackId": track_id,
            "title": title,
            "format": "bili-xml",
            "encoding": "gzip+base64",
            "payload": payload,
        }
        if prefs:
            body["prefs"] = prefs
        return self._post("/v1/danmaku/load", body)

    def unload(self, media_key=None, track_id=None):
        if not self._ensure_token():
            return False
        payload = {}
        if media_key:
            payload["mediaKey"] = media_key
        if track_id:
            payload["trackId"] = track_id
        return self._post("/v1/danmaku/unload", payload)

    def _ensure_token(self):
        now = time.time()
        if self._token and self._port and now - self._token_timestamp < self._retry_seconds:
            return True
        if not xbmcvfs.exists(self._token_path):
            return False
        try:
            handle = xbmcvfs.File(self._token_path, "r")
        except Exception as exc:
            log(f"Unable to open IPC token file: {exc}", xbmc.LOGWARNING)
            return False
        try:
            data = handle.read()
        finally:
            handle.close()
        if isinstance(data, bytes):
            data = data.decode("utf-8")
        try:
            parsed = json.loads(data)
            self._token = parsed.get("token")
            self._port = int(parsed.get("port"))
            self._token_timestamp = now
            return bool(self._token and self._port)
        except Exception as exc:  # pylint: disable=broad-except
            log(f"Invalid IPC token data: {exc}", xbmc.LOGWARNING)
            self._token = None
            self._port = None
            return False

    def _post(self, path, payload):
        url = f"http://127.0.0.1:{self._port}{path}"
        headers = {
            "Authorization": f"Bearer {self._token}",
            "Content-Type": "application/json",
        }
        data = json.dumps(payload or {}).encode("utf-8")
        req = request.Request(url, data=data, headers=headers, method="POST")
        try:
            with request.urlopen(req, timeout=5) as resp:
                return 200 <= resp.status < 300
        except error.HTTPError as exc:
            if exc.code == 401:
                self._token = None
            log(f"IPC HTTP error: {exc}", xbmc.LOGWARNING)
            return False
        except Exception as exc:  # pylint: disable=broad-except
            log(f"IPC request failed: {exc}", xbmc.LOGWARNING)
            return False
