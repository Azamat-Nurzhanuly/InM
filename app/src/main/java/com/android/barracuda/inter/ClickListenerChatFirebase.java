package com.android.barracuda.inter;

import android.view.View;

import java.io.IOException;

public interface ClickListenerChatFirebase {

  void clickImageChat(View view, int position, String nameUser, String urlPhotoUser, String urlPhotoClick);

  void clickImageMapChat(View view, int position, String latitude, String longitude);

  void clickAudioPlayChat(View view, int position) throws IOException;

  void clickAudioStopChat(View view, int position);

}
