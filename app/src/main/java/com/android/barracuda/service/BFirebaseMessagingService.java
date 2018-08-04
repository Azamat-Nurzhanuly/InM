package com.android.barracuda.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.android.barracuda.MainActivity;
import com.android.barracuda.R;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.messaging.RemoteMessage.Notification;

import java.util.Map;

public class BFirebaseMessagingService extends FirebaseMessagingService {
  private static final String TAG = "BFirebaseMessagingServ";

  public static final String NOTIFICATION_CHANNEL_ID = "com.android.barracuda.channel_id";
  public static final String NOTIFICATION_CHANNEL_NAME = "com.android.barracuda.channel_name";


  private NotificationManager mNotificationManager;
  private NotificationCompat.Builder mBuilder;

  @Override
  public void onMessageReceived(RemoteMessage remoteMessage) {
    super.onMessageReceived(remoteMessage);

    Log.d(TAG, "Message From: " + remoteMessage.getFrom());

    Map<String, String> data = remoteMessage.getData();

    if (data.size() > 0) {
      Log.d(TAG, "Message data: " + data);
    }

    Notification notification = remoteMessage.getNotification();
    if (notification != null) {
      String body = notification.getBody();
      Log.d(TAG, "Message Notification: " + body);
      sendNotification(body);
    }
  }

  private void sendNotification(String body) {
    Intent intent = new Intent(this, MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    Uri notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);


    mBuilder = new NotificationCompat.Builder(this)
      .setSmallIcon(R.mipmap.ic_email)
      .setContentTitle("Barracuda Notification")
      .setContentText(body)
      .setAutoCancel(true)
      .setSound(notificationSound)
      .setContentIntent(pendingIntent);

    mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      int importance = NotificationManager.IMPORTANCE_HIGH;


      String channelId = this.getString(R.string.default_notification_channel_id);
      NotificationChannel channel = new NotificationChannel(channelId, NOTIFICATION_CHANNEL_NAME, importance);

      channel.setDescription(body);
      channel.enableLights(true);
      channel.setLightColor(Color.RED);
      channel.enableVibration(true);
      channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
      assert mNotificationManager != null;
      mBuilder.setChannelId(channelId);

      mNotificationManager.createNotificationChannel(channel);
    }

    assert mNotificationManager != null;
    mNotificationManager.notify(0, mBuilder.build());
  }
}
