package com.maestro.reminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public final class ReminderStore {
    private static final String PREFS = "maestro_reminders";
    private static final String KEY_ITEMS = "items";
    private ReminderStore() {}

    public static List<Reminder> load(Context context) {
        List<Reminder> result = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) result.add(Reminder.fromJson(array.getJSONObject(i)));
        } catch (JSONException ignored) {}
        return result;
    }

    public static void save(Context context, List<Reminder> items) {
        JSONArray array = new JSONArray();
        for (Reminder item : items) {
            try { array.put(item.toJson()); } catch (JSONException ignored) {}
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    public static Reminder find(Context context, long id) {
        for (Reminder item : load(context)) if (item.id == id) return item;
        return null;
    }

    public static void delete(Context context, long id) {
        List<Reminder> items = load(context);
        for (int i = items.size() - 1; i >= 0; i--) if (items.get(i).id == id) items.remove(i);
        save(context, items);
    }
}
