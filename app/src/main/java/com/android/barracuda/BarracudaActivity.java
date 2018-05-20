package com.android.barracuda;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;

import com.android.barracuda.data.SharedPreferenceHelper;

public class BarracudaActivity extends AppCompatActivity {

  public static final String COLOR_DARK_BLUE = "dark_blue";
  public static final String COLOR_BLUE = "blue";
  public static final String COLOR_PURPLE = "purple";
  public static final String COLOR_ORANGE = "orange";

  public void setTheme() {
    SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
    final String color = sharedPreferences.getString(SharedPreferenceHelper.SHARE_COLOR, "");

    switch (color) {
      case COLOR_DARK_BLUE: {
        getApplication().setTheme(R.style.AppDarkBlueTheme);
        setTheme(R.style.AppDarkBlueTheme);
        break;
      }
      case COLOR_BLUE: {
        getApplication().setTheme(R.style.AppBlueTheme);
        setTheme(R.style.AppBlueTheme);
        break;
      }
      case COLOR_PURPLE: {
        getApplication().setTheme(R.style.AppPurpleTheme);
        setTheme(R.style.AppPurpleTheme);
        break;
      }
      case COLOR_ORANGE: {
        getApplication().setTheme(R.style.AppOrangeTheme);
        setTheme(R.style.AppOrangeTheme);
        break;
      }
      default: {
        getApplication().setTheme(R.style.AppDarkBlueTheme);
        setTheme(R.style.AppDarkBlueTheme);
      }
    }
  }
}
