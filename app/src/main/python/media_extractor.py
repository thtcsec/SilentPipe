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
        'noplaylist': False, # Enable playlist support
        'quiet': True,
        'no_warnings': True,
        'extract_flat': False, # Resolve playlists
        # TikTok often requires a User-Agent to avoid 403 or redirect to login
        'http_headers': {
            'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 14_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Mobile/15E148 Safari/604.1',
        },
    }
    
    # Spotify Support via YouTube Search
    if "spotify.com" in url:
        try:
             import urllib.request
             import re
             import ssl
             ctx = ssl.create_default_context()
             ctx.check_hostname = False
             ctx.verify_mode = ssl.CERT_NONE
             headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'}
             req = urllib.request.Request(url, headers=headers)
             with urllib.request.urlopen(req, context=ctx) as response:
                 html = response.read().decode('utf-8')
             
             # Try custom regex for spotify title
             title_match = re.search(r'<title>(.*?)</title>', html)
             if title_match:
                 page_title = title_match.group(1).replace(" | Spotify", "")
                 # Search this on YouTube
                 url = f"ytsearch1:{page_title}"
        except Exception as e:
             return {"error": f"Lỗi Spotify Scrape: {str(e)}"}

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            
            # Playlist Detection
            if 'entries' in info:
                entries = list(info.get('entries'))
                if entries:
                    # Return the first entry but mark as playlist (or just play it)
                    # For now we take the first item to play
                    info = entries[0]
            
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
        
        # Fallback for TikTok (Music or Video) if yt-dlp fails
        if "tiktok.com" in url and "ytsearch" not in url:
            print(f"DEBUG: Entering TikTok Fallback for {url}")
            try:
                import urllib.request
                import re
                import json
                import ssl

                # Android often has issues with SSL certs in embedded Python, bypass it for scraping
                ctx = ssl.create_default_context()
                ctx.check_hostname = False
                ctx.verify_mode = ssl.CERT_NONE

                headers = {
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
                    'Referer': 'https://www.tiktok.com/'
                }
                
                req = urllib.request.Request(url, headers=headers)
                with urllib.request.urlopen(req, context=ctx) as response:
                    html = response.read().decode('utf-8')
                
                print("DEBUG: HTML fetched, searching for URL...")

                # Strategy 1: "playUrl":"..."
                matches = re.findall(r'"playUrl":"(.*?)"', html)
                audio_url = None
                
                for match in matches:
                    try:
                        # Decode potential unicode escapes
                        cleaned = match.encode('utf-8').decode('unicode_escape').replace(r'\/', '/')
                        if cleaned.startswith('http') and len(cleaned) > 10:
                            audio_url = cleaned
                            print(f"DEBUG: Found Strategy 1 URL: {audio_url}")
                            break
                    except:
                        continue
                
                if not audio_url:
                     # Strategy 2: Look for direct mp3/m4a patterns
                     media_matches = re.findall(r'"(https?://[^"]+?\.(?:mp3|m4a|aac).*?)"', html)
                     if media_matches:
                         audio_url = media_matches[0].encode('utf-8').decode('unicode_escape').replace(r'\/', '/')
                         print(f"DEBUG: Found Strategy 2 URL: {audio_url}")

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
                     print("DEBUG: No URL found in fallback")
                     return {"error": "Không thể tìm thấy link nhạc trong trang này. TikTok có thể đã đổi cấu trúc."}
            except Exception as fallback_e:
                 print(f"DEBUG: Fallback exception: {fallback_e}")
                 return {"error": f"Lỗi fallback TikTok: {str(fallback_e)}"}
        
        # Capture full traceback for debugging if not TikTok or fallback failed strangely logic
        return {"error": f"Lỗi xử lý (Extraction Error):\n{error_msg}\n\n{traceback.format_exc()}"}

        # Capture full traceback for debugging
        return {"error": f"Lỗi xử lý (Extraction Error):\n{error_msg}\n\n{traceback.format_exc()}"}
