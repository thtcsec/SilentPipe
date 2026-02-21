package com.tuhoang.silentpipe.core.manager;

import android.content.Context;
import android.content.SharedPreferences;
import com.tuhoang.silentpipe.ui.main.SettingsActivity;
import org.json.JSONArray;
import org.json.JSONException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ErrorLogManager {
    private static ErrorLogManager instance;
    private static final String PREF_NAME = "error_logs";
    private static final String KEY_LOGS = "logs";
    private static final int MAX_LOGS = 15;
    private final SharedPreferences prefs;
    private final SimpleDateFormat dateFormat;

    private ErrorLogManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }

    public static synchronized ErrorLogManager getInstance(Context context) {
        if (instance == null) {
            instance = new ErrorLogManager(context.getApplicationContext());
        }
        return instance;
    }

    public void logError(String tag, String message) {
        String timestamp = dateFormat.format(new Date());
        String entry = "[" + timestamp + "] " + tag + ": " + message;
        
        List<String> logs = getLogs();
        logs.add(0, entry); // Add to top
        
        if (logs.size() > MAX_LOGS) {
            logs = logs.subList(0, MAX_LOGS);
        }
        
        saveLogs(logs);
    }

    public List<String> getLogs() {
        String json = prefs.getString(KEY_LOGS, "[]");
        List<String> logs = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                logs.add(array.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return logs;
    }

    public void clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply();
    }

    private void saveLogs(List<String> logs) {
        JSONArray array = new JSONArray(logs);
        prefs.edit().putString(KEY_LOGS, array.toString()).apply();
    }
}
