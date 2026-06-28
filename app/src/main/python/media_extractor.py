import sys
import traceback
import re
import json
import time

# Capture import errors to report them to the UI
IMPORT_ERROR = None
try:
    import yt_dlp
except Exception:
    IMPORT_ERROR = traceback.format_exc()


# --- Constants ---
_MOBILE_UA = (
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) '
    'AppleWebKit/605.1.15 (KHTML, like Gecko) '
    'Version/17.4 Mobile/15E148 Safari/604.1'
)

_TIKTOK_GENERIC_WORDS = {"tiktok", "video", "music", "make your day"}


def _safe_result(result):
    """Ensure the result is always a valid dict with expected keys.
    This prevents NoneType errors on the Java/Chaquopy side."""
    if result is None or not isinstance(result, dict):
        return {"error": "Extraction returned no data (None result)."}
    # Ensure all expected keys exist with safe defaults
    result.setdefault("title", "Unknown Title")
    result.setdefault("duration", 0)
    result.setdefault("thumbnail", "")
    result.setdefault("url", "")
    result.setdefault("uploader", "Unknown")
    result.setdefault("view_count", 0)
    result.setdefault("source_info", "")
    return result


# ──────────────────────────────────────────────
#  TikTok manual extraction (fallback)
# ──────────────────────────────────────────────

def _extract_tiktok_manual(url):
    """Scrape TikTok page directly when yt-dlp fails."""
    try:
        import urllib.request
        import urllib.error
        import ssl

        if not url or "tiktok.com" not in url:
            return {"error": "Not a TikTok URL"}

        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE

        headers = {
            'User-Agent': _MOBILE_UA,
            'Referer': 'https://www.tiktok.com/',
        }

        html = _fetch_with_retries(url, headers, ctx, max_retries=3)
        if not html:
            return {"error": "Không thể kết nối TikTok sau nhiều lần thử."}

        audio_url = (
            _tiktok_strategy_play_url(html)
            or _tiktok_strategy_direct_media(html)
            or _tiktok_strategy_universal_data(html)
        )

        if not audio_url:
            return {"error": "Không thể lấy link nhạc TikTok (Manual Parse Failed)."}

        title = _extract_tiktok_title(html) or f"TikTok Audio {int(time.time())}"

        return {
            "title": title,
            "duration": 0,
            "thumbnail": "",
            "url": audio_url,
            "uploader": "TikTok",
            "view_count": 0,
            "source_info": "Source: TikTok (Manual)",
        }

    except Exception as e:
        return {"error": f"Lỗi lấy nhạc TikTok: {e}"}


def _fetch_with_retries(url, headers, ssl_ctx, max_retries=3):
    """Fetch URL content with retry + exponential backoff."""
    import urllib.request
    import urllib.error

    if not url:
        return None

    last_error = None
    for attempt in range(max_retries):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, context=ssl_ctx, timeout=15) as resp:
                raw = resp.read()
                if raw is None:
                    return None
                return raw.decode('utf-8', errors='replace')
        except urllib.error.HTTPError as e:
            if e.code in (429, 403, 503):
                last_error = e
                time.sleep(2 * (attempt + 1))
            else:
                last_error = e
                break
        except Exception as e:
            last_error = e
            time.sleep(1)
    return None


def _unescape_url(raw):
    """Decode unicode-escaped URL and normalise slashes."""
    if not raw:
        return ""
    try:
        return raw.encode().decode('unicode_escape').replace(r'\/', '/')
    except Exception:
        return raw.replace(r'\/', '/')


def _tiktok_strategy_play_url(html):
    if not html:
        return None
    for m in re.findall(r'"playUrl":"(.*?)"', html):
        cleaned = _unescape_url(m)
        if cleaned and cleaned.startswith('http') and len(cleaned) > 10:
            return cleaned
    return None


def _tiktok_strategy_direct_media(html):
    if not html:
        return None
    matches = re.findall(r'"(https?://[^"]+?\.(?:mp3|m4a|aac).*?)"', html)
    if matches:
        return _unescape_url(matches[0])
    return None


def _tiktok_strategy_universal_data(html):
    if not html:
        return None
    m = re.search(
        r'<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>',
        html,
    )
    if not m:
        return None
    try:
        s_data = m.group(1)
        u = re.search(r'"playUrl":"(.*?)"', s_data)
        if u:
            return _unescape_url(u.group(1))
    except Exception:
        pass
    return None


def _extract_tiktok_title(html):
    """Best-effort title extraction from TikTok HTML."""
    if not html:
        return f"TikTok Audio {int(time.time())}"

    title = None

    # 1. og:title
    og = re.search(r'<meta property="og:title" content="(.*?)"', html)
    if og:
        title = og.group(1).strip()

    # 2. <title> tag fallback
    if not title or _is_generic_tiktok_title(title):
        tag = re.search(r'<title>(.*?)</title>', html)
        if tag:
            raw = (
                tag.group(1)
                .replace(" | TikTok", "")
                .replace("TikTok - Make Your Day", "")
                .strip()
            )
            if raw and not _is_generic_tiktok_title(raw):
                title = raw

    # 3. og:description fallback
    if not title or _is_generic_tiktok_title(title):
        desc = re.search(r'<meta property="og:description" content="(.*?)"', html)
        if desc:
            d = desc.group(1).strip()
            if " | " in d:
                d = d.split(" | ")[0]
            d = re.sub(r'(?i)\s*on TikTok.*$', '', d).strip()
            if d and len(d) > 3:
                title = d

    if not title or _is_generic_tiktok_title(title):
        title = f"TikTok Audio {int(time.time())}"

    return title


def _is_generic_tiktok_title(title):
    if not title:
        return True
    lower = title.lower().strip()
    return not lower or any(w in lower for w in _TIKTOK_GENERIC_WORDS)


# ──────────────────────────────────────────────
#  Main extraction entry point
# ──────────────────────────────────────────────

def extract_info(url, prefer_hq=False, cookie_str=None, show_video=False):
    """
    Main entry point called from Java/Chaquopy.
    GUARANTEES: Always returns a dict, never None.
    The dict will always have at minimum an 'error' key OR valid media keys.
    """
    try:
        return _extract_info_internal(url, prefer_hq, cookie_str, show_video)
    except Exception as e:
        # Ultimate safety net - no matter what happens, return a dict
        return {"error": f"Lỗi nghiêm trọng (Critical Error):\n{e}\n\n{traceback.format_exc()}"}


def _extract_info_internal(url, prefer_hq=False, cookie_str=None, show_video=False):
    """Internal extraction logic. May raise exceptions (caught by extract_info)."""
    if IMPORT_ERROR:
        return {"error": f"Lỗi khởi động Python (Import Error):\n{IMPORT_ERROR}"}

    if not url or not isinstance(url, str) or not url.strip():
        return {"error": "URL không hợp lệ (empty or invalid)."}

    url = url.strip()

    # TikTok music links → skip yt-dlp, go manual
    if "tiktok.com" in url and "/music/" in url:
        result = _extract_tiktok_manual(url)
        return _safe_result(result)

    # ── Build yt-dlp options ──
    if show_video:
        fmt = 'best[ext=mp4]/best'
    else:
        fmt = 'bestaudio/best' if prefer_hq else 'worstaudio[ext=m4a]/worstaudio/worst'

    ydl_opts = {
        'format': fmt,
        'noplaylist': True,
        'quiet': True,
        'no_warnings': True,
        'extract_flat': False,
        'socket_timeout': 20,
        'http_headers': {'User-Agent': _MOBILE_UA},
    }

    # ── Cookie support ──
    import tempfile, os
    cookie_temp_path = None
    if cookie_str and isinstance(cookie_str, str) and len(cookie_str.strip()) > 20:
        try:
            fd, cookie_temp_path = tempfile.mkstemp(suffix=".txt", prefix="cookies_")
            with os.fdopen(fd, 'w') as f:
                f.write(cookie_str)
            ydl_opts['cookiefile'] = cookie_temp_path
        except Exception:
            cookie_temp_path = None  # Ensure it's None if creation failed

    try:
        result = _run_ytdlp(url, ydl_opts)
        return _safe_result(result)
    except Exception as e:
        # Fallback for TikTok if yt-dlp fails entirely
        if "tiktok.com" in url:
            fallback = _extract_tiktok_manual(url)
            return _safe_result(fallback)
        return {"error": f"Lỗi xử lý (Extraction Error):\n{e}\n\n{traceback.format_exc()}"}
    finally:
        if cookie_temp_path:
            try:
                import os as _os
                _os.remove(cookie_temp_path)
            except OSError:
                pass


def _run_ytdlp(url, ydl_opts):
    """Run yt-dlp extraction with proper None-safety."""
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=False)

        # ── FIX: info can be None when extraction silently fails ──
        if info is None:
            return {
                "error": (
                    "yt-dlp returned no data. The URL may be invalid, "
                    "geo-restricted, or the service is temporarily unavailable."
                )
            }

        # Ensure info is a dict (some extractors return unexpected types)
        if not isinstance(info, dict):
            return {"error": f"yt-dlp returned unexpected type: {type(info).__name__}"}

        # ── Resolve stream URL ──
        stream_url = info.get("url") or ""
        if not stream_url:
            formats = info.get("formats")
            if formats and isinstance(formats, list):
                # Prefer audio-only formats
                audio_fmts = [
                    f for f in formats
                    if isinstance(f, dict)
                    and f.get('acodec') != 'none'
                    and f.get('vcodec') in ('none', None)
                ]
                if audio_fmts:
                    stream_url = audio_fmts[-1].get('url') or ""
                elif formats:
                    # Fallback: last format with a URL
                    for f in reversed(formats):
                        if isinstance(f, dict) and f.get('url'):
                            stream_url = f.get('url')
                            break

        if not stream_url:
            return {"error": "yt-dlp could not find a playable stream URL."}

        # ── Title cleanup ──
        title = info.get("title") or "Unknown Title"
        extractor = (info.get("extractor") or "").lower()
        is_tiktok = "tiktok.com" in url or "tiktok" in extractor

        if is_tiktok and _is_generic_tiktok_title(title):
            desc = info.get("description") or ""
            if desc and len(desc) > 5 and "TikTok" not in desc[:20]:
                title = desc.split("\n")[0][:100]

        source_info = "Source: TikTok" if is_tiktok else "Source: YouTube"

        return {
            "title": title,
            "duration": info.get("duration") or 0,
            "thumbnail": info.get("thumbnail") or "",
            "url": stream_url,
            "uploader": info.get("uploader") or "Unknown",
            "view_count": info.get("view_count") or 0,
            "source_info": source_info,
        }
