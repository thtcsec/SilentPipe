package com.tuhoang.silentpipe.core.datasource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public class DownloaderImpl extends Downloader {

    private static DownloaderImpl instance;
    private final OkHttpClient client;

    private DownloaderImpl(OkHttpClient.Builder builder) {
        this.client = builder.build();
    }

    public static DownloaderImpl getInstance() {
        if (instance == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS);
            instance = new DownloaderImpl(builder);
        }
        return instance;
    }

    @Override
    public Response execute(@NonNull Request request) throws IOException, ReCaptchaException {
        String httpMethod = request.httpMethod();
        String url = request.url();
        Map<String, List<String>> headers = request.headers();
        byte[] data = request.dataToSend();

        okhttp3.Request.Builder okRequestBuilder = new okhttp3.Request.Builder()
                .url(url);

        // Add headers
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                String key = entry.getKey();
                for (String value : entry.getValue()) {
                    okRequestBuilder.addHeader(key, value);
                }
            }
        }

        // Handle Body
        RequestBody body = null;
        if (data != null) {
             MediaType contentType = null;
             if (headers != null) {
                 List<String> contentTypes = headers.get("Content-Type");
                 if (contentTypes != null && !contentTypes.isEmpty()) {
                     contentType = MediaType.parse(contentTypes.get(0));
                 }
             }
             body = RequestBody.create(contentType, data);
        }

        // Set Method
        okRequestBuilder.method(httpMethod, body);

        // Execute
        try (okhttp3.Response okResponse = client.newCall(okRequestBuilder.build()).execute()) {
             ResponseBody responseBody = okResponse.body();
             String responseString = null;
             if (responseBody != null) {
                 responseString = responseBody.string();
             }

             return new Response(
                     okResponse.code(),
                     okResponse.message(),
                     okResponse.headers().toMultimap(),
                     responseString,
                     okResponse.request().url().toString()
             );
        }
    }
}
