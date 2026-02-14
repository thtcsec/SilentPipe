package com.tuhoang.silentpipe.core.extractor;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class OkHttpDownloader extends Downloader {

    private final OkHttpClient client;

    public OkHttpDownloader() {
        this.client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(request.url());

        // Add default headers if not present
        builder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        builder.addHeader("Accept-Language", "en-US,en;q=0.9");

        if (request.headers() != null) {
            for (Map.Entry<String, List<String>> h : request.headers().entrySet()) {
                for (String v : h.getValue()) {
                    builder.addHeader(h.getKey(), v);
                }
            }
        }

        okhttp3.Request okhttpRequest = builder.build();
        okhttp3.Response response = client.newCall(okhttpRequest).execute();

        okhttp3.ResponseBody body = response.body();
        String responseBody = body != null ? new String(body.bytes()) : "";

        return new Response(
            response.code(),
            response.message(),
            response.headers().toMultimap(),
            responseBody,
            response.request().url().toString()
        );
    }
}
