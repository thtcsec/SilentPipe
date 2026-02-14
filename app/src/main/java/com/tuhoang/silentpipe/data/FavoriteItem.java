package com.tuhoang.silentpipe.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorites")
public class FavoriteItem {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String title;
    public String url;
    public String thumbnail;
    public String uploader;
    public long duration;
    public long timestamp;

    public FavoriteItem(String title, String url, String thumbnail, String uploader, long duration, long timestamp) {
        this.title = title;
        this.url = url;
        this.thumbnail = thumbnail;
        this.uploader = uploader;
        this.duration = duration;
        this.timestamp = timestamp;
    }
}
