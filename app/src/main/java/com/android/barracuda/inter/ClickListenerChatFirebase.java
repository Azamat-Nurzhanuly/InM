package com.android.barracuda.inter;

import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.VideoView;

public interface ClickListenerChatFirebase {

  void clickImageChat(View view, int position);

  void clickVideoChat(View view, int position);

  void clickImageMapChat(View view, int position, String latitude, String longitude);

  void clickAudioPlayChat(View view, int position, ImageView play_button, ImageView pause_button, SeekBar seek) throws Exception;

  void clickAudioPauseChat(View view, int position, ImageView play_button, ImageView pause_button, SeekBar seek) throws Exception;

  void seekChange(View view, ImageView play_button, ImageView pause_button, SeekBar seekBar) throws Exception;

}
