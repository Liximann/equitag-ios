package de.kopfnummer.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Speichert die Pferdeliste lokal auf dem Gerät (SharedPreferences, JSON). */
public final class HorseStore {
    private static final String PREFS = "kopfnummer";
    private static final String KEY_HORSES = "horses";
    private static final String KEY_ROTATE = "rotate180";
    private static final String KEY_LANG = "language";
    private static final String KEY_INVERT = "invert";

    private final SharedPreferences prefs;

    public HorseStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Horse> load() {
        List<Horse> list = new ArrayList<Horse>();
        String raw = prefs.getString(KEY_HORSES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                list.add(new Horse(o.getLong("id"), o.getString("name"), o.getString("number")));
            }
        } catch (JSONException e) {
            // Beschädigte Daten: mit leerer Liste weitermachen.
        }
        return list;
    }

    public void save(List<Horse> horses) {
        JSONArray array = new JSONArray();
        try {
            for (Horse h : horses) {
                JSONObject o = new JSONObject();
                o.put("id", h.id);
                o.put("name", h.name);
                o.put("number", h.number);
                array.put(o);
            }
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(KEY_HORSES, array.toString()).apply();
    }

    public boolean isRotate180() {
        return prefs.getBoolean(KEY_ROTATE, false);
    }

    public void setRotate180(boolean value) {
        prefs.edit().putBoolean(KEY_ROTATE, value).apply();
    }

    public boolean isInvert() {
        return prefs.getBoolean(KEY_INVERT, false);
    }

    public void setInvert(boolean value) {
        prefs.edit().putBoolean(KEY_INVERT, value).apply();
    }

    /** Leer = Systemsprache. */
    public String getLanguage() {
        return prefs.getString(KEY_LANG, "");
    }

    public void setLanguage(String code) {
        prefs.edit().putString(KEY_LANG, code).commit();
    }
}
