package com.android.barracuda.util;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.sinch.gson.Gson;
import com.sinch.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Objects;

public class SharedPrefUtil {

  public static String FRIENDS_MAP = "friends_map";

  public static void saveHashMap(String key , Object obj, Activity activity) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
    SharedPreferences.Editor editor = prefs.edit();
    Gson gson = new Gson();
    String json = gson.toJson(obj);
    editor.putString(key,json);
    editor.apply();
  }

  public static HashMap<String, String> getFriendsMap(String key, Activity activity) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
    Gson gson = new Gson();
    String json = prefs.getString(key,"");

    if(Objects.equals(json, "")) return null;

    java.lang.reflect.Type type = new TypeToken<HashMap<String, String>>(){}.getType();
    HashMap<String, String> obj = gson.fromJson(json, type);
    return obj;
  }
}
