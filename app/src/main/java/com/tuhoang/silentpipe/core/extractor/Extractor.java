package com.tuhoang.silentpipe.core.extractor;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.IOException;

public class Extractor {

    static {
        try {
            NewPipe.init(new OkHttpDownloader());
        } catch (Exception e) {
            android.util.Log.e("Extractor", "Extraction failed", e);
        }
    }

    public static StreamInfo extractStreamInfo(String url) throws ExtractionException, IOException {
        StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor(url);
        extractor.fetchPage();
        return StreamInfo.getInfo(extractor);
    }
}
