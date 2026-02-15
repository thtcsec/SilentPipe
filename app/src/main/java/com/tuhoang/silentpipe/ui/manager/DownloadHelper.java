package com.tuhoang.silentpipe.ui.manager;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;
import com.tuhoang.silentpipe.data.FavoriteItem;

public class DownloadHelper {
    private final Context context;

    public DownloadHelper(Context context) {
        this.context = context;
    }

    public void downloadMedia(String streamUrl, FavoriteItem mediaItem) {
        if (streamUrl == null) {
             Toast.makeText(context, "No media to download", Toast.LENGTH_SHORT).show();
             return;
        }

        try {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = Uri.parse(streamUrl);
            
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("SilentPipe: " + (mediaItem != null ? mediaItem.title : "Media"));
            request.setDescription("Downloading video/audio...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "SilentPipe/" + (mediaItem != null ? mediaItem.title.replaceAll("[^a-zA-Z0-9.-]", "_") : "download") + ".mp4");
            
            downloadManager.enqueue(request);
            Toast.makeText(context, "Downloading to Music/SilentPipe...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
