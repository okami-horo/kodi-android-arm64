import os
import re
from urllib.parse import unquote, urlparse

import xbmc
import xbmcvfs


ADDON_ID = "script.service.dandanplay"
LOG_PREFIX = "[script.service.dandanplay] "


def log(message, level=xbmc.LOGINFO):
    xbmc.log(LOG_PREFIX + str(message), level)


def translate_path(path):
    return xbmcvfs.translatePath(path)


def ensure_dir(path):
    if not xbmcvfs.exists(path):
        xbmcvfs.mkdirs(path)


def to_local_path(url):
    if not url:
        return None
    if url.startswith("file://"):
        parsed = urlparse(url)
        return unquote(parsed.path or "")
    if url.startswith("/"):
        return url
    if url.startswith("special://"):
        return translate_path(url)
    return None


def media_key(path, size, mtime):
    return f"{path}|{size}|{int(mtime)}"


def sanitize_keyword(path):
    base = os.path.splitext(os.path.basename(path))[0]
    cleaned = re.sub(r"[\[\]\(\)\{\}]+", " ", base)
    cleaned = re.sub(r"[._]+", " ", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned.strip()
