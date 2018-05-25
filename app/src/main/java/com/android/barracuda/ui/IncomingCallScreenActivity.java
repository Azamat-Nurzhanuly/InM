package com.android.barracuda.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
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
import com.sinch.android.rtc.MissingPermissionException;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallEndCause;
import com.sinch.android.rtc.calling.CallListener;
import com.sinch.android.rtc.video.VideoCallListener;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static com.android.barracuda.data.StaticConfig.CALL_INCOMING;

public class IncomingCallScreenActivity extends ChatActivity {

  static final String TAG = IncomingCallScreenActivity.class.getSimpleName();
  private String mCallId;
  private AudioPlayer mAudioPlayer;
  private TextView mCallState;
  private static Context mContext;


  private static Context getContext() {
    return mContext;
  }

  private static void setContext(Context context) {
    mContext = context;
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.incoming);

    FloatingActionButton answer = (FloatingActionButton) findViewById(R.id.answerButton);
    answer.setOnClickListener(mClickListener);
    FloatingActionButton decline = (FloatingActionButton) findViewById(R.id.declineButton);
    decline.setOnClickListener(mClickListener);

    mCallState = (TextView) findViewById(R.id.callState);

    mAudioPlayer = new AudioPlayer(this);
    mAudioPlayer.playRingtone();
    mCallId = getIntent().getStringExtra(SinchService.CALL_ID);

    setContext(this);
  }

  @Override
  protected void onServiceConnected() {
    Call call = getSinchServiceInterface().getCall(mCallId);
    if (call != null) {
      call.addCallListener(new SinchCallListener());
      final TextView remoteUser = (TextView) findViewById(R.id.remoteUser);

      System.out.println(FirebaseDatabase.getInstance().getReference().child("user/" + call.getRemoteUserId()));

      FirebaseDatabase.getInstance().getReference().child("user/" + call.getRemoteUserId()).addValueEventListener(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot snapshot) {
          HashMap hashUser = (HashMap) snapshot.getValue();
          User userInfo = new User();
          assert hashUser != null;
          userInfo.name = (String) hashUser.get("name");
          userInfo.phoneNumber = (String) hashUser.get("phoneNumber");
          userInfo.avata = (String) hashUser.get("avata");
          remoteUser.setText(userInfo.name);
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
        }
      });


      if (call.getDetails().isVideoOffered()) {

        mCallState.setText("Видео звонок");

      } else {
        mCallState.setText("Аудио звонок");
      }

    } else {
      Log.e(TAG, "Started with invalid callId, aborting");
      finish();
    }
  }

  private void answerClicked() {
    mAudioPlayer.stopRingtone();
    Call call = getSinchServiceInterface().getCall(mCallId);
    if (call != null) {
      try {
        call.answer();
        Intent intent = new Intent(this, CallScreenActivity.class);
        intent.putExtra(SinchService.CALL_ID, mCallId);
        startActivity(intent);
      } catch (MissingPermissionException e) {
        ActivityCompat.requestPermissions(this, new String[]{e.getRequiredPermission()}, 0);
      }
    } else {
      finish();
    }
  }

  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
    } else {
    }
  }

  private void declineClicked() {
    mAudioPlayer.stopRingtone();
    Call call = getSinchServiceInterface().getCall(mCallId);
    if (call != null) {
      call.hangup();
    }
    finish();
  }

  private class SinchCallListener implements CallListener, VideoCallListener {

    @Override
    public void onCallEnded(Call call) {
      CallEndCause cause = call.getDetails().getEndCause();
      Log.d(TAG, "Call ended, cause: " + cause.toString());
      mAudioPlayer.stopRingtone();
      finish();
    }

    @Override
    public void onCallEstablished(Call call) {
      Log.d(TAG, "Call established");
    }

    @Override
    public void onCallProgressing(Call call) {
      Log.d(TAG, "Call progressing");

      saveCallInCallsHistory(call);
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
            call.type = CALL_INCOMING;
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


    //VIDEO CALL

    @Override
    public void onVideoTrackAdded(Call call) {
      // Display some kind of icon showing it's a video call
    }

    @Override
    public void onVideoTrackPaused(Call call) {
      // Display some kind of icon showing it's a video call
    }

    @Override
    public void onVideoTrackResumed(Call call) {
      // Display some kind of icon showing it's a video call
    }

  }

  private OnClickListener mClickListener = new OnClickListener() {
    @Override
    public void onClick(View v) {
      switch (v.getId()) {
        case R.id.answerButton:
          answerClicked();
          break;
        case R.id.declineButton:
          declineClicked();
          break;
      }
    }
  };
}
