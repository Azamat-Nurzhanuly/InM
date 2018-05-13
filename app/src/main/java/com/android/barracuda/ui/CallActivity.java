package com.android.barracuda.ui;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.barracuda.R;
import com.android.barracuda.data.StaticConfig;
import com.google.firebase.database.FirebaseDatabase;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.Sinch;
import com.sinch.android.rtc.SinchClient;
import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallClient;
import com.sinch.android.rtc.calling.CallClientListener;
import com.sinch.android.rtc.calling.CallListener;

import java.util.List;

import static com.android.barracuda.data.StaticConfig.SINCH_HOST;
import static com.android.barracuda.data.StaticConfig.SINCH_KEY;
import static com.android.barracuda.data.StaticConfig.SINCH_SECRET;


public class CallActivity extends AppCompatActivity implements View.OnClickListener {

  SinchClient sinchClient;
  Button call_button;
  TextView call_state;

  //sinch
  Call call;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_call);

    String number = FirebaseDatabase.getInstance().getReference().child("user/" + StaticConfig.UID).getKey();

    if (number != null && !number.isEmpty()) {
      sinchClient = Sinch.getSinchClientBuilder()
        .context(this)
        .userId(number)
        .applicationKey(SINCH_KEY)
        .applicationSecret(SINCH_SECRET)
        .environmentHost(SINCH_HOST)
        .build();


      sinchClient.setSupportCalling(true);
      sinchClient.setSupportActiveConnectionInBackground(true);

      sinchClient.startListeningOnActiveConnection();
      sinchClient.start();

      sinchClient.getCallClient().addCallClientListener(new SinchCallClientListener());

    }

    call_button = (Button) findViewById(R.id.call_button);
    call_state = (TextView) findViewById(R.id.callState);


    call_button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (call == null) {
          call = sinchClient.getCallClient().callUser("RpxEvurP9JTLdREMsaFnFpmIRYq2");
          call_button.setText("Hang Up");

          call.addCallListener(new SinchCallListener());
        } else {
          call.hangup();
          call = null;
          call_button.setText("Call");
        }

      }
    });

  }

  @Override
  public void onBackPressed() {
    Intent result = new Intent();
    setResult(RESULT_OK, result);
    this.finish();
  }

  @Override
  public void onClick(View view) {

    switch (view.getId()) {
    }
  }


  class SinchCallListener implements CallListener {
    @Override
    public void onCallProgressing(Call call) {
      call_state.setText("ringing");
    }

    @Override
    public void onCallEstablished(Call call) {

      setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
      call_state.setText("connected");


    }

    @Override
    public void onCallEnded(Call call) {
      setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
      System.out.println(call.getDetails().getError());
      call_state.setText("ended");
    }

    @Override
    public void onShouldSendPushNotification(Call call, List<PushPair> list) {

    }
  }

  private class SinchCallClientListener implements CallClientListener {
    @Override
    public void onIncomingCall(CallClient callClient, Call incomingCall) {
      //Pick up the call!

      call = incomingCall;
      call.answer();
      call.addCallListener(new SinchCallListener());
      call_button.setText("Hang Up");
    }
  }
}

