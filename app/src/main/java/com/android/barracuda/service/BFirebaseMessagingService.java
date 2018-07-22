package com.android.barracuda.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.android.barracuda.MainActivity;
import com.android.barracuda.R;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.messaging.RemoteMessage.Notification;

import java.util.Map;

import static android.support.v4.app.NotificationCompat.Builder;

public class BFirebaseMessagingService extends FirebaseMessagingService {
  private static final String TAG = "BFirebaseMessagingServ";
  public static final String NOTIFICATION_CHANNEL_ID = "com.android.barracuda.channel_id";
  public static final String NOTIFICATION_CHANNEL_NAME = "com.android.barracuda.channel_name";


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
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
    Uri notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);


    Builder notificationBuilder = new Builder(this)
      .setSmallIcon(R.mipmap.ic_email)
      .setContentTitle("Barracuda Notification")
      .setContentText(body)
      .setAutoCancel(true)
      .setSound(notificationSound)
      .setContentIntent(pendingIntent);

    NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    assert notificationManager != null;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
        NOTIFICATION_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_DEFAULT);
      notificationManager.createNotificationChannel(channel);
    }

    notificationManager.notify(0, notificationBuilder.build());
  }
}
