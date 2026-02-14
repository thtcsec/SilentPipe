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
        # 'socket_timeout': 10, # Optional: Timeout
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
        # Capture full traceback for debugging
        return {"error": f"Lỗi xử lý (Extraction Error):\n{str(e)}\n\n{traceback.format_exc()}"}
