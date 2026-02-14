package com.tuhoang.silentpipe.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {FavoriteItem.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract FavoriteDao favoriteDao();
}
