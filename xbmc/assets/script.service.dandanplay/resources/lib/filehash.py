import hashlib

import xbmc
import xbmcvfs

from .utils import log


HASH_LIMIT = 16 * 1024 * 1024
BUFFER_SIZE = 256 * 1024


def _read_chunk(handle, size):
    reader = getattr(handle, "readBytes", None)
    if callable(reader):
        return reader(size)
    data = handle.read(size)
    if isinstance(data, str):
        return data.encode("utf-8", errors="ignore")
    return data


def compute_hash(path):
    try:
        handle = xbmcvfs.File(path, "rb")
    except Exception as exc:
        log(f"Unable to open file for hashing: {exc}", xbmc.LOGWARNING)
        return None
    try:
        md5 = hashlib.md5()
        remaining = HASH_LIMIT
        while remaining > 0:
            chunk = _read_chunk(handle, min(BUFFER_SIZE, remaining))
            if not chunk:
                break
            md5.update(chunk)
            remaining -= len(chunk)
        return md5.hexdigest()
    except Exception as exc:
        log(f"Hashing failed: {exc}", xbmc.LOGWARNING)
        return None
    finally:
        handle.close()
