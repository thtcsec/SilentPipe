import sys
import traceback

# Capture import errors to report them to the UI
IMPORT_ERROR = None
try:
    import yt_dlp
except Exception:
    IMPORT_ERROR = traceback.format_exc()

def _extract_tiktok_manual(url):
    try:
        import urllib.request
        import urllib.error
        import re
        import json
        import ssl
        import time

        print(f"DEBUG: Starting manual extraction for {url}")

        # Bypass SSL
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE

        headers = {
            'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1',
            'Referer': 'https://www.tiktok.com/'
        }
        
        if "tiktok.com" not in url:
            raise Exception("Not a TikTok URL")

        # Retry Logic
        max_retries = 3
        last_error = None
        
        for attempt in range(max_retries):
            try:
                print(f"DEBUG: Attempt {attempt + 1} for {url}")
                req = urllib.request.Request(url, headers=headers)
                with urllib.request.urlopen(req, context=ctx, timeout=15) as response:
                    html = response.read().decode('utf-8')
                print("DEBUG: HTML fetched successfully")
                break # Success
            except urllib.error.HTTPError as e:
                print(f"DEBUG: HTTP Error {e.code}")
                if e.code in [429, 403, 503]:
                    last_error = e
                    time.sleep(2 * (attempt + 1)) # Backoff
                    continue
                else:
                    raise e # Fatal error
            except Exception as e:
                print(f"DEBUG: Network Error {e}")
                last_error = e
                time.sleep(1)
                continue
        else:
            raise last_error or Exception("Failed after retries")

        audio_url = None
        
        # Strategy 1: "playUrl" in JSON
        matches = re.findall(r'"playUrl":"(.*?)"', html)
        for match in matches:
            cleaned = match.encode().decode('unicode_escape').replace(r'\/', '/')
            if cleaned.startswith('http') and len(cleaned) > 10:
                audio_url = cleaned
                print("DEBUG: Found Strategy 1 URL")
                break
        
        if not audio_url:
             # Strategy 2: Direct mp3/m4a
             media_matches = re.findall(r'"(https?://[^"]+?\.(?:mp3|m4a|aac).*?)"', html)
             if media_matches:
                 audio_url = media_matches[0].encode().decode('unicode_escape').replace(r'\/', '/')
                 print("DEBUG: Found Strategy 2 URL")

        if not audio_url:
            # Strategy 3: UNIVERSAL_DATA
            json_match = re.search(r'<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>', html)
            if json_match:
                 try:
                    data = json.loads(json_match.group(1))
                    # Quick regex search on the JSON string to avoid complex traversal
                    s_data = json.dumps(data)
                    u_match = re.search(r'"playUrl":"(.*?)"', s_data)
                    if u_match:
                        audio_url = u_match.group(1).encode().decode('unicode_escape').replace(r'\/', '/')
                        print("DEBUG: Found Strategy 3 URL")
                 except Exception as e:
                    print(f"DEBUG: Strategy 3 failed: {e}")

        if audio_url:
             title = "TikTok Audio"
             
             # 1. Try og:title (Most reliable)
             og_title = re.search(r'<meta property="og:title" content="(.*?)"', html)
             if og_title:
                 title = og_title.group(1).strip()
             
             # 2. Try regex for specific music title pattern often in TikTok Music
             # <h1 data-e2e="music-title">Title</h1> (need to check if this exists in mobile view, maybe not)
             
             # 3. Try title tag as fallback
             if title == "TikTok Audio" or "TikTok" in title:
                 title_tag = re.search(r'<title>(.*?)</title>', html)
                 if title_tag:
                     raw_title = title_tag.group(1).strip()
                     # Clean up
                     raw_title = raw_title.replace(" | TikTok", "").replace("TikTok - Make Your Day", "").strip()
                     if raw_title and "TikTok" not in raw_title:
                         title = raw_title
             
             # 4. Description fallback
             if title == "TikTok Audio":
                 desc_match = re.search(r'<meta property="og:description" content="(.*?)"', html)
                 if desc_match:
                     desc = desc_match.group(1).strip()
                     if "on TikTok" not in desc:
                         title = desc
                     else:
                         parts = desc.split(" | ")
                         if len(parts) > 0:
                              title = parts[0]

             # Final Cleanup
             if not title: title = f"TikTok Audio {int(time.time())}"
             
             return {
                "title": title,
                "duration": 0,
                "thumbnail": "",
                "url": audio_url,
                "uploader": "TikTok",
                "view_count": 0,
                "source_info": "Source: TikTok (Manual)"
            }
        else:
             return {"error": "Không thể lấy link nhạc TikTok (Manual Parse Failed)."}

    except Exception as e:
        print(f"DEBUG: Manual extraction error: {e}")
        return {"error": f"Lỗi lấy nhạc TikTok: {str(e)}"}

def extract_info(url, prefer_hq=False, cookie_str=None, show_video=False):
    if IMPORT_ERROR:
        return {"error": f"Lỗi khởi động Python (Import Error):\n{IMPORT_ERROR}"}

    # Optimization: If TikTok Music link, skip yt-dlp and go straight to manual
    if "tiktok.com/music" in url or "tiktok.com" in url and "/music/" in url:
        return _extract_tiktok_manual(url)

    # Normal yt-dlp logic
    # Based on old working version (simple is better)
    if show_video:
        format_selection = 'bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best'
    else:
        format_selection = 'bestaudio/best' if prefer_hq else 'worst[ext=m4a]/worst'
    
    ydl_opts = {
        'format': format_selection,
        'noplaylist': True,
        'quiet': True,
        'no_warnings': True,
        'extract_flat': False,
        'ignoreerrors': True,
        'http_headers': {
            'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1',
        },
    }

    # Cookie support ...
    cookie_temp_path = None
    if cookie_str and len(cookie_str.strip()) > 20: 
        import tempfile
        import os
        try:
            fd, cookie_temp_path = tempfile.mkstemp(suffix=".txt", prefix="cookies_")
            with os.fdopen(fd, 'w') as f:
                f.write(cookie_str)
            ydl_opts['cookiefile'] = cookie_temp_path
        except Exception as ce:
            print(f"Error creating cookie file: {ce}")

    try:
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                
                stream_url = info.get("url")
                if not stream_url:
                    formats = info.get("formats", [])
                    audio_formats = [f for f in formats if f.get('acodec') != 'none' and f.get('vcodec') == 'none']
                    if audio_formats:
                        stream_url = audio_formats[-1].get('url')
                    elif formats:
                         stream_url = formats[-1].get('url')

                if not stream_url:
                    raise Exception("No URL found by yt-dlp")

                source_info = "Source: YouTube"
                if "tiktok.com" in url or "tiktok" in info.get("extractor", "").lower():
                    source_info = "Source: TikTok"

                return {
                    "title": info.get("title", "Unknown Title"),
                    "duration": info.get("duration", 0),
                    "thumbnail": info.get("thumbnail", ""),
                    "url": stream_url,
                    "uploader": info.get("uploader", "Unknown"),
                    "view_count": info.get("view_count", 0),
                    "source_info": source_info
                }
        except Exception as e:
            # Fallback for TikTok (Video) if yt-dlp fails
            if "tiktok.com" in url:
                return _extract_tiktok_manual(url)
            
            return {"error": f"Lỗi xử lý (Extraction Error):\n{str(e)}\n\n{traceback.format_exc()}"}
    finally:
        if cookie_temp_path and os.path.exists(cookie_temp_path):
            try:
                os.remove(cookie_temp_path)
            except:
                pass
