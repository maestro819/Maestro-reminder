package com.maestro.reminder;

import org.json.JSONException;
import org.json.JSONObject;

public class Reminder {
    public static final String ONCE = "ONCE";
    public static final String DAILY = "DAILY";
    public static final String WEEKLY = "WEEKLY";
    public static final String WEEKDAYS = "WEEKDAYS";
    public static final String ACTIVITY = "ACTIVITY";
    public static final String SLEEP = "SLEEP";

    public long id; public String title; public String note; public String icon; public String kind;
    public String category; public String message; public long triggerAt; public long anchorAt; public String repeat; public boolean enabled;

    public Reminder(long id, String title, String note, long triggerAt, String repeat, boolean enabled) {
        this(id, title, note, "⏰", ACTIVITY, "OTHER", "", triggerAt, repeat, enabled);
    }
    public Reminder(long id, String title, String note, String icon, String kind, long triggerAt, String repeat, boolean enabled) {
        this(id, title, note, icon, kind, "OTHER", "", triggerAt, repeat, enabled);
    }
    public Reminder(long id, String title, String note, String icon, String kind, String category, String message, long triggerAt, String repeat, boolean enabled) {
        this.id=id; this.title=title; this.note=note==null?"":note; this.icon=icon==null||icon.isEmpty()?"⏰":icon;
        this.kind=SLEEP.equals(kind)?SLEEP:ACTIVITY; this.category=category==null||category.isEmpty()?"OTHER":category; this.message=message==null?"":message;
        this.triggerAt=triggerAt; this.anchorAt=triggerAt; this.repeat=repeat; this.enabled=enabled;
    }
    public JSONObject toJson() throws JSONException {
        JSONObject json=new JSONObject(); json.put("id",id); json.put("title",title); json.put("note",note); json.put("icon",icon); json.put("kind",kind);
        json.put("category",category); json.put("message",message); json.put("triggerAt",triggerAt); json.put("anchorAt",anchorAt); json.put("repeat",repeat); json.put("enabled",enabled); return json;
    }
    public static Reminder fromJson(JSONObject json) throws JSONException {
        Reminder r=new Reminder(json.getLong("id"),json.optString("title","Pengingat"),json.optString("note",""),json.optString("icon","⏰"),json.optString("kind",ACTIVITY),json.optString("category","OTHER"),json.optString("message",""),json.getLong("triggerAt"),json.optString("repeat",ONCE),json.optBoolean("enabled",true));
        r.anchorAt=json.optLong("anchorAt",r.triggerAt); return r;
    }
}
