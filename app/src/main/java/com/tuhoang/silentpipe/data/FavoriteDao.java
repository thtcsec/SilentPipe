package com.tuhoang.silentpipe.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    List<FavoriteItem> getAll();

    @Insert
    void insert(FavoriteItem item);

    @Delete
    void delete(FavoriteItem item);
    
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE url = :url LIMIT 1)")
    boolean isFavorite(String url);

    @Query("DELETE FROM favorites WHERE url = :url")
    void deleteByUrl(String url);

    @Update
    void update(FavoriteItem item);
}
