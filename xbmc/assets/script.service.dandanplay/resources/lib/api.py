import gzip
import json
import ssl
from html import escape
from urllib import error, parse, request

import xbmc

from .utils import log


class DandanAPI:
    def __init__(self, timeout=15):
        self._hosts = [
            "https://api.dandanplay.net",
            "http://139.217.235.62:16001",
        ]
        self._timeout = timeout
        self._ssl = ssl.create_default_context()
        self._app_id = ""
        self._app_secret = ""

    def set_credentials(self, app_id, app_secret):
        self._app_id = (app_id or "").strip()
        self._app_secret = (app_secret or "").strip()

    def match_episode(self, file_hash, file_name, file_size, duration):
        payload = {
            "fileHash": file_hash,
            "fileName": file_name or "unknown",
            "fileSize": int(file_size or 0),
            "videoDuration": int(duration or 0),
            "matchMode": "hashOnly",
        }
        data = self._request("POST", "/api/v2/match", json_body=payload)
        if not data:
            return None
        if not data.get("isMatched"):
            return None
        matches = data.get("matches") or []
        for item in matches:
            if item.get("episodeId"):
                return item
        return None

    def search_episode(self, keyword):
        if not keyword:
            return None
        params = {"anime": keyword}
        data = self._request("GET", "/api/v2/search/episodes", params=params)
        if not data:
            return None
        for anime in data.get("animes", []):
            episodes = anime.get("episodes") or []
            if not episodes:
                continue
            episode = dict(episodes[0])
            episode.setdefault("animeTitle", anime.get("animeTitle", ""))
            return episode
        return None

    def download_comments(self, episode_id):
        if not episode_id:
            return []
        params = {"withRelated": "true"}
        data = self._request("GET", f"/api/v2/comment/{episode_id}", params=params)
        if not data:
            return []
        return data.get("comments") or []

    def build_bili_xml(self, comments):
        lines = ["<i>"]
        for item in comments:
            attr = item.get("p")
            text = item.get("m", "")
            if not attr:
                continue
            lines.append(f'<d p="{attr}">{escape(text)}</d>')
        lines.append("</i>")
        return "\n".join(lines).encode("utf-8")

    def _request(self, method, path, json_body=None, params=None):
        last_error = None
        encoded_body = None
        headers = {
            "User-Agent": "Kodi-Dandanplay/0.1",
            "Accept": "application/json",
            "Accept-Encoding": "gzip",
        }
        if self._app_id and self._app_secret:
            headers["X-AppId"] = self._app_id
            headers["X-AppSecret"] = self._app_secret
        if json_body is not None:
            encoded_body = json.dumps(json_body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        query = ""
        if params:
            query = "?" + parse.urlencode(params)
        for host in self._hosts:
            url = f"{host}{path}{query}"
            req = request.Request(url, data=encoded_body, headers=headers, method=method)
            context = self._ssl if host.startswith("https://") else None
            try:
                with request.urlopen(req, timeout=self._timeout, context=context) as resp:
                    data = resp.read()
                    if resp.headers.get("Content-Encoding") == "gzip":
                        data = gzip.decompress(data)
                    if not data:
                        return None
                    return json.loads(data.decode("utf-8"))
            except error.URLError as exc:
                last_error = exc
                continue
            except Exception as exc:  # pylint: disable=broad-except
                last_error = exc
                break
        if last_error:
            log(f"Dandanplay API request failed: {last_error}", xbmc.LOGWARNING)
        return None
