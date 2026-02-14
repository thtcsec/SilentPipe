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
        if "Unsupported URL" in error_msg:
             if "tiktok.com/music" in url:
                 return {"error": "Đây là link Nhạc nền TikTok (Music), không phải Video. Vui lòng chia sẻ link Video."}
             return {"error": "Không hỗ trợ loại link này (Unsupported URL). Hãy thử link Video trực tiếp."}
        
        # Capture full traceback for debugging
        return {"error": f"Lỗi xử lý (Extraction Error):\n{error_msg}\n\n{traceback.format_exc()}"}
