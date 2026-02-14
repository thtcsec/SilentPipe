import sys
import traceback

# Capture import errors to report them to the UI
IMPORT_ERROR = None
try:
    import yt_dlp
except Exception:
    IMPORT_ERROR = traceback.format_exc()

def extract_info(url):
    # If import failed, return the error immediately
    if IMPORT_ERROR:
        return {"error": f"Lỗi khởi động Python (Import Error):\n{IMPORT_ERROR}"}

    ydl_opts = {
        'format': 'bestaudio/best',
        'noplaylist': True,
        'quiet': True,
        'no_warnings': True,
        'extract_flat': False,
        # TikTok often requires a User-Agent to avoid 403 or redirect to login
        'http_headers': {
            'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 14_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Mobile/15E148 Safari/604.1',
        },
    }
    
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            
            # Try to get URL from top level or formats
            stream_url = info.get("url")
            if not stream_url:
                formats = info.get("formats", [])
                # Simple filter for best audio if 'url' not at top
                audio_formats = [f for f in formats if f.get('acodec') != 'none' and f.get('vcodec') == 'none']
                if audio_formats:
                    # Get the last one (usually highest quality)
                    stream_url = audio_formats[-1].get('url')
                elif formats:
                     stream_url = formats[-1].get('url')

            if not stream_url:
                return {"error": "Không tìm thấy link media (No URL found)"}

            return {
                "title": info.get("title", "Unknown Title"),
                "duration": info.get("duration", 0),
                "thumbnail": info.get("thumbnail", ""),
                "url": stream_url,
                "uploader": info.get("uploader", "Unknown"),
                "view_count": info.get("view_count", 0)
            }
    except Exception as e:
        error_msg = str(e)
        
        # Fallback for TikTok Music (yt-dlp 'Unsupported URL')
        if "Unsupported URL" in error_msg and "tiktok.com/music" in url:
            try:
                import urllib.request
                import re
                import json

                # TikTok requires User-Agent
                headers = {
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
                    'Referer': 'https://www.tiktok.com/'
                }
                req = urllib.request.Request(url, headers=headers)
                with urllib.request.urlopen(req) as response:
                    html = response.read().decode('utf-8')

                # Strategy 1: Look for "playUrl" in deeply nested JSON (SIGI_STATE or __UNIVERSAL_DATA...)
                # Matches: "playUrl":"https://..."
                # NOTE: URLs in JSON are often escaped like https:\\u002F\\u002F...
                matches = re.findall(r'"playUrl":"(.*?)"', html)
                
                audio_url = None
                for match in matches:
                    # Clean up standard JSON escaping
                    cleaned = match.encode().decode('unicode_escape').replace(r'\/', '/')
                    if cleaned.startswith('http') and len(cleaned) > 10:
                        audio_url = cleaned
                        break
                
                if not audio_url:
                     # Strategy 2: Look immediately for mp3/m4a inside quotes
                     # This is looser but might catch direct source tags
                     media_matches = re.findall(r'"(https?://[^"]+?\.(?:mp3|m4a|aac).*?)"', html)
                     if media_matches:
                         audio_url = media_matches[0].encode().decode('unicode_escape').replace(r'\/', '/')

                if audio_url:
                     title_match = re.search(r'<title>(.*?)</title>', html)
                     title = title_match.group(1).replace(" | TikTok", "") if title_match else "TikTok Music"
                     
                     return {
                        "title": title,
                        "duration": 0,
                        "thumbnail": "",
                        "url": audio_url,
                        "uploader": "TikTok Music",
                        "view_count": 0
                    }
                else:
                     return {"error": "Không thể lấy link nhạc từ trang này (Parse Error)."}
            except Exception as fallback_e:
                 return {"error": f"Lỗi lấy nhạc TikTok (Fallback Error):\n{str(fallback_e)}"}

        # Capture full traceback for debugging
        return {"error": f"Lỗi xử lý (Extraction Error):\n{error_msg}\n\n{traceback.format_exc()}"}
