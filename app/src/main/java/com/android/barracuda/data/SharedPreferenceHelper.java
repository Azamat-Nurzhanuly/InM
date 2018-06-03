package com.android.barracuda.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.barracuda.model.User;


public class SharedPreferenceHelper {
  private static SharedPreferenceHelper instance = null;
  private static SharedPreferences preferences;
  private static SharedPreferences.Editor editor;
  private static String SHARE_USER_INFO = "userinfo";
  private static String SHARE_KEY_NAME = "name";
  private static String SHARE_KEY_PHONE_NUMBER = "phoneNumber";
  private static String SHARE_KEY_AVATA = "avata";
  private static String SHARE_KEY_UID = "uid";

  public static final String USER_SELECTION = "user_selection";
  public static final String SHARE_COLOR = "color";
  public static final String INCOGNITO = "incognito";

  private SharedPreferenceHelper() {
  }

  public static SharedPreferenceHelper getInstance(Context context) {
    if (instance == null) {
      instance = new SharedPreferenceHelper();
      preferences = context.getSharedPreferences(SHARE_USER_INFO, Context.MODE_PRIVATE);
      editor = preferences.edit();
    }
    return instance;
  }

  public void saveUserInfo(User user) {
    editor.putString(SHARE_KEY_NAME, user.name);
    editor.putString(SHARE_KEY_PHONE_NUMBER, user.phoneNumber);
    editor.putString(SHARE_KEY_AVATA, user.avata);
    editor.putString(SHARE_KEY_UID, StaticConfig.UID);
    editor.apply();
  }

  public User getUserInfo() {
    String userName = preferences.getString(SHARE_KEY_NAME, "");
    String phoneNumber = preferences.getString(SHARE_KEY_PHONE_NUMBER, "");
    String avatar = preferences.getString(SHARE_KEY_AVATA, "default");

    User user = new User();
    user.name = userName;
    user.phoneNumber = phoneNumber;
    user.avata = avatar;

    return user;
  }

  public String getUID() {
    return preferences.getString(SHARE_KEY_UID, "");
  }

}
