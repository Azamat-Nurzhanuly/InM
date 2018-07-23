package com.android.barracuda.ui;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.barracuda.R;
import com.android.barracuda.data.CallDB;
import com.android.barracuda.model.AudioPlayer;
import com.android.barracuda.model.User;
import com.android.barracuda.service.SinchService;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallEndCause;
import com.sinch.android.rtc.calling.CallListener;
import com.sinch.android.rtc.calling.CallState;
import com.sinch.android.rtc.video.VideoCallListener;
import com.sinch.android.rtc.video.VideoController;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static com.android.barracuda.data.StaticConfig.CALL_OUTGOING;

public class CallScreenActivity extends ChatActivity {


  //AUDIO CALL
  static final String TAG = CallScreenActivity.class.getSimpleName();

  private AudioPlayer mAudioPlayer;
  private Timer mTimer;
  private UpdateCallDurationTask mDurationTask;

  private String mCallId;

  private TextView mCallDuration;
  private TextView mCallState;
  private TextView mCallerName;

  private static Context mContext;


  private class UpdateCallDurationTask extends TimerTask {

    @Override
    public void run() {
      CallScreenActivity.this.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          updateCallDuration();
        }
      });
    }
  }

  private static Context getContext() {
    return mContext;
  }

  private static void setContext(Context context) {
    mContext = context;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {

    return false;
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContext(this);
    setContentView(R.layout.callscreen);

    mAudioPlayer = new AudioPlayer(this);
    mCallDuration = (TextView) findViewById(R.id.callDuration);
    mCallerName = (TextView) findViewById(R.id.remoteUser);
    mCallState = (TextView) findViewById(R.id.callState);
    FloatingActionButton endCallButton = (FloatingActionButton) findViewById(R.id.hangupButton);

    endCallButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        endCall();
      }
    });
    mCallId = getIntent().getStringExtra(SinchService.CALL_ID);

    removeVideoViews();
  }

  @Override
  public void onServiceConnected() {
    Call call = getSinchServiceInterface().getCall(mCallId);
    if (call != null) {
      call.addCallListener(new SinchCallListener());
    } else {
      Log.e(TAG, "Started with invalid callId, aborting.");
      finish();
    }

    updateUI();
  }

  private void updateUI() {
    if (getSinchServiceInterface() == null) {
      return; // early
    }

    Call call = getSinchServiceInterface().getCall(mCallId);
    if (call != null) {


      FirebaseDatabase.getInstance().getReference().child("user/" + call.getRemoteUserId()).addValueEventListener(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot snapshot) {
          HashMap hashUser = (HashMap) snapshot.getValue();
          User userInfo = new User();
          assert hashUser != null;
          userInfo.name = (String) hashUser.get("name");
          userInfo.phoneNumber = (String) hashUser.get("phoneNumber");
          userInfo.avata = (String) hashUser.get("avata");
          mCallerName.setText(userInfo.name);
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
        }
      });

      if (call.getDetails().isVideoOffered()) {

        mCallState.setText("Видео звонок");

        addLocalView();
        if (call.getState() == CallState.ESTABLISHED) {
          addRemoteView();
        }

        AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        assert audioManager != null;
        audioManager.setMode(AudioManager.STREAM_VOICE_CALL);
        audioManager.setSpeakerphoneOn(true);
      } else {
        mCallState.setText("Аудио звонок");
      }
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    mTimer = new Timer();
    mDurationTask = new UpdateCallDurationTask();
    mTimer.schedule(mDurationTask, 0, 500);
    updateUI();
  }

  @Override
  public void onStop() {
    super.onStop();
    mDurationTask.cancel();
    mTimer.cancel();
    removeVideoViews();
  }

  @Override
  public void onPause() {
    super.onPause();
    mDurationTask.cancel();
    mTimer.cancel();
  }

  @Override
  public void onResume() {
    super.onResume();
    mTimer = new Timer();
    mDurationTask = new UpdateCallDurationTask();
    mTimer.schedule(mDurationTask, 0, 500);
    updateUI();
  }

  @Override
  public void onBackPressed() {
    // User should exit activity by ending call, not by going back.
  }

  private void endCall() {
    mAudioPlayer.stopProgressTone();
    Call call = getSinchServiceInterface().getCall(mCallId);
    if (call != null) {
      call.hangup();
    }
    finish();
  }

  private String formatTimespan(int totalSeconds) {
    long minutes = totalSeconds / 60;
    long seconds = totalSeconds % 60;
    return String.format(Locale.US, "%02d:%02d", minutes, seconds);
  }

  private void updateCallDuration() {
    Call call = getSinchServiceInterface().getCall(mCallId);
    if (call != null) {
      mCallDuration.setText(formatTimespan(call.getDetails().getDuration()));
    }
  }

  private class SinchCallListener implements CallListener, VideoCallListener {
    @Override
    public void onCallEnded(Call call) {
      CallEndCause cause = call.getDetails().getEndCause();
      Log.d(TAG, "Call ended. Reason: " + cause.toString());
      mAudioPlayer.stopProgressTone();
      setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
      String endMsg = "Call ended: " + call.getDetails().toString();
      endCall();
      saveCallInCallsHistory(call);
    }

    @Override
    public void onCallEstablished(Call call) {
      Log.d(TAG, "Call established");
      mAudioPlayer.stopProgressTone();
      mCallState.setText("Соединено");
//      setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

      if (call.getDetails().isVideoOffered()) {
      } else {
        getSinchServiceInterface().getAudioController().disableSpeaker();
      }
    }

    @Override
    public void onCallProgressing(Call call) {
      Log.d(TAG, "Call progressing");
      mAudioPlayer.playProgressTone();
    }

    private void saveCallInCallsHistory(Call call) {
      final String id = call.getRemoteUserId();
      final String callId = call.getRemoteUserId();

      FirebaseDatabase.getInstance().getReference().child("user/" + id).addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {


          if (dataSnapshot.getValue() != null) {
            com.android.barracuda.model.Call call = new com.android.barracuda.model.Call();
            HashMap mapUserInfo = (HashMap) dataSnapshot.getValue();
            call.name = (String) mapUserInfo.get("name");
            call.phoneNumber = (String) mapUserInfo.get("phoneNumber");
            call.avata = (String) mapUserInfo.get("avata");
            call.id = id;
            call.type = CALL_OUTGOING;
            call.callId = String.valueOf(new Date().getTime());
            CallDB.getInstance(getContext()).addCall(call);
          }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
      });
    }

    @Override
    public void onShouldSendPushNotification(Call call, List<PushPair> pushPairs) {
      // Send a push through your push provider here, e.g. GCM
    }


    //VIDEO CALL LISTENERS
    @Override
    public void onVideoTrackAdded(Call call) {

      Log.d(TAG, "Video track added");
      addRemoteView();
    }

    @Override
    public void onVideoTrackPaused(Call call) {

    }

    @Override
    public void onVideoTrackResumed(Call call) {

    }

  }


  //VIDEO CALL
  public boolean mLocalVideoViewAdded = false;
  public boolean mRemoteVideoViewAdded = false;

  private void addLocalView() {
    if (mLocalVideoViewAdded || getSinchServiceInterface() == null) {
      return; //early
    }
    final VideoController vc = getSinchServiceInterface().getVideoController();
    if (vc != null) {
      RelativeLayout localView = (RelativeLayout) findViewById(R.id.localVideo);
      localView.addView(vc.getLocalView());
      localView.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          vc.toggleCaptureDevicePosition();
        }
      });
      mLocalVideoViewAdded = true;
    }
  }

  private void addRemoteView() {
    if (mRemoteVideoViewAdded || getSinchServiceInterface() == null) {
      return; //early
    }
    final VideoController vc = getSinchServiceInterface().getVideoController();
    if (vc != null) {

      LinearLayout view = (LinearLayout) findViewById(R.id.remoteVideo);
      view.addView(vc.getRemoteView());
      mRemoteVideoViewAdded = true;
    }
  }


  public void removeVideoViews() {
    if (getSinchServiceInterface() == null) {
      return; // early
    }

    VideoController vc = getSinchServiceInterface().getVideoController();
    if (vc != null) {
      LinearLayout view = (LinearLayout) findViewById(R.id.remoteVideo);
      view.removeView(vc.getRemoteView());

      RelativeLayout localView = (RelativeLayout) findViewById(R.id.localVideo);
      localView.removeView(vc.getLocalView());
      mLocalVideoViewAdded = false;
      mRemoteVideoViewAdded = false;
    }
  }


}
