import sys
import traceback

# Capture import errors to report them to the UI
IMPORT_ERROR = None
try:
    import yt_dlp
except Exception:
    IMPORT_ERROR = traceback.format_exc()

def extract_info(url, prefer_hq=False, cookie_str=None):
    # If import failed, return the error immediately
    if IMPORT_ERROR:
        return {"error": f"Lỗi khởi động Python (Import Error):\n{IMPORT_ERROR}"}

    # Based on old working version (simple is better)
    # HQ Logic: 'bestaudio/best' vs 'worst[ext=m4a]/worst' (Data Saver)
    format_selection = 'bestaudio/best' if prefer_hq else 'worst[ext=m4a]/worst'
    
    ydl_opts = {
        'format': format_selection,
        'noplaylist': True,
        'quiet': True,
        'no_warnings': True,
        'extract_flat': False,
        'ignoreerrors': True,
        'http_headers': {
             # Use a modern iPhone User-Agent which often avoids captchas/blocks better than desktop
            'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1',
        },
    }

    # Cookie support: Write string to temp file if provided
    cookie_temp_path = None
    if cookie_str and len(cookie_str.strip()) > 20: # Crude check for valid-ish content
        import tempfile
        import os
        try:
            # Create a secure temp file for cookies
            fd, cookie_temp_path = tempfile.mkstemp(suffix=".txt", prefix="cookies_")
            with os.fdopen(fd, 'w') as f:
                f.write(cookie_str)
            ydl_opts['cookiefile'] = cookie_temp_path
        except Exception as ce:
            print(f"Error creating cookie file: {ce}")

    
    # Spotify Support REMOVED as requested by user to isolate TikTok issues.
    # Logic is now purely yt-dlp + TikTok fallback.

    try:
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                
                # ... Result processing ...
                stream_url = info.get("url")
                if not stream_url:
                    formats = info.get("formats", [])
                    audio_formats = [f for f in formats if f.get('acodec') != 'none' and f.get('vcodec') == 'none']
                    if audio_formats:
                        stream_url = audio_formats[-1].get('url')
                    elif formats:
                         stream_url = formats[-1].get('url')

                if not stream_url:
                     # Force raise to trigger fallback if yt-dlp fails to get a URL
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
            error_msg = str(e)
            
            # Fallback for TikTok Music (yt-dlp 'Unsupported URL' or blocked)
            if "tiktok.com" in url:
                try:
                    import urllib.request
                    import re
                    import json
                    import ssl

                    # Android often has issues with SSL certs in embedded Python, bypass it for scraping
                    ctx = ssl.create_default_context()
                    ctx.check_hostname = False
                    ctx.verify_mode = ssl.CERT_NONE

                    # TikTok mobile UA
                    headers = {
                        'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1',
                        'Referer': 'https://www.tiktok.com/'
                    }
                    req = urllib.request.Request(url, headers=headers)
                    with urllib.request.urlopen(req, context=ctx) as response:
                        html = response.read().decode('utf-8')

                    audio_url = None
                    
                    # Strategy 1: Look for "playUrl" in JSON structure
                    matches = re.findall(r'"playUrl":"(.*?)"', html)
                    for match in matches:
                        cleaned = match.encode().decode('unicode_escape').replace(r'\/', '/')
                        if cleaned.startswith('http') and len(cleaned) > 10:
                            audio_url = cleaned
                            break
                    
                    if not audio_url:
                         # Strategy 2: Look for direct audio mp3/m4a/aac inside quotes
                         media_matches = re.findall(r'"(https?://[^"]+?\.(?:mp3|m4a|aac).*?)"', html)
                         if media_matches:
                             audio_url = media_matches[0].encode().decode('unicode_escape').replace(r'\/', '/')
                             
                    if not audio_url:
                        # Strategy 3: Check helper nextjs script tag often found in mobile view
                        json_match = re.search(r'<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">(.*?)</script>', html)
                        if json_match:
                            try:
                                data = json.loads(json_match.group(1))
                                # Traverse potentially nested dict for 'playUrl' or 'music'
                                # This is generic traversal as structure changes often
                                s_data = str(data)
                                u_match = re.search(r"'playUrl':\s*'(.*?)'", s_data)
                                if u_match:
                                    audio_url = u_match.group(1)
                            except:
                                pass

                    if audio_url:
                         # Extract Title - PRIORITIZE Caption/Description over generic Title
                         title = "TikTok Video"
                         
                         # Try og:description first (usually contains the user caption)
                         desc_match = re.search(r'<meta property="og:description" content="(.*?)"', html)
                         if desc_match:
                             desc = desc_match.group(1)
                             # Cleanup "User (@user) on TikTok | Watch ..."
                             if "on TikTok" not in desc and "Watch" not in desc:
                                 title = desc
                             else:
                                 # Try to extract the part before " | " or just take it all if short
                                 title = desc.split(" | ")[0]
                         else:
                             # Fallback to Title tag but clean it
                             title_match = re.search(r'<title>(.*?)</title>', html)
                             if title_match:
                                 raw_title = title_match.group(1)
                                 # Remove " | TikTok" suffix
                                 title = raw_title.replace(" | TikTok", "").replace("TikTok - Make Your Day", "").strip()
                                 if not title: title = "TikTok Video"

                         return {
                            "title": title,
                            "duration": 0,
                            "thumbnail": "",
                            "url": audio_url,
                            "uploader": "TikTok User",
                            "view_count": 0,
                            "source_info": "Source: TikTok (Fallback)"
                        }
                    else:
                         return {"error": "Không thể lấy link nhạc từ trang này (Parse Error)."}
                except Exception as fallback_e:
                     return {"error": f"Lỗi lấy nhạc TikTok (Fallback Error):\n{str(fallback_e)}"}
            
            # Capture full traceback for debugging separate from TikTok fallback
            return {"error": f"Lỗi xử lý (Extraction Error):\n{error_msg}\n\n{traceback.format_exc()}"}
    finally:
        if cookie_temp_path and os.path.exists(cookie_temp_path):
            try:
                os.remove(cookie_temp_path)
            except:
                pass
