package com.tuhoang.silentpipe.core.repository;

import com.tuhoang.silentpipe.core.extractor.Extractor;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import java.io.IOException;

public class VideoRepository {

    public interface VideoCallback {
        void onSuccess(StreamInfo streamInfo);
        void onError(Exception e);
    }

    public void getVideoInfo(String url, VideoCallback callback) {
        new Thread(() -> {
            try {
                StreamInfo info = Extractor.extractStreamInfo(url);
                callback.onSuccess(info);
            } catch (ExtractionException | IOException e) {
                callback.onError(e);
            }
        }).start();
    }
}
