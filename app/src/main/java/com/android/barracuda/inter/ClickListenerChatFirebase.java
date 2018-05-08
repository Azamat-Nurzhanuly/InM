package com.android.barracuda.inter;

import android.view.View;
import android.widget.ImageView;

public interface ClickListenerChatFirebase {

  void clickImageChat(View view, int position, String nameUser, String urlPhotoUser, String urlPhotoClick);

  void clickImageMapChat(View view, int position, String latitude, String longitude);

  void clickAudioPlayChat(View view, int position, ImageView play_button, ImageView pause_button) throws Exception;

  void clickAudioStopChat(View view, int position, ImageView play_button, ImageView pause_button) throws Exception;

}
