package com.android.barracuda.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.barracuda.BuildConfig;
import com.android.barracuda.MainActivity;
import com.android.barracuda.R;
import com.android.barracuda.data.SharedPreferenceHelper;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.inter.ClickListenerChatFirebase;
import com.android.barracuda.model.Consersation;
import com.android.barracuda.model.FileModel;
import com.android.barracuda.model.Message;
import com.android.barracuda.service.SinchService;
import com.bumptech.glide.Glide;
import com.devlomi.record_view.OnBasketAnimationEnd;
import com.devlomi.record_view.OnRecordClickListener;
import com.devlomi.record_view.OnRecordListener;
import com.devlomi.record_view.RecordButton;
import com.devlomi.record_view.RecordView;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.gordonwong.materialsheetfab.MaterialSheetFab;
import com.sinch.android.rtc.MissingPermissionException;
import com.sinch.android.rtc.SinchError;
import com.sinch.android.rtc.calling.Call;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import de.hdodenhof.circleimageview.CircleImageView;
import io.codetail.animation.SupportAnimator;
import io.codetail.animation.ViewAnimationUtils;

import static com.android.barracuda.data.StaticConfig.INTENT_KEY_CHAT_ID;
import static com.android.barracuda.ui.ChatActivity.MESSAGE_TYPE_AUDIO;
import static com.android.barracuda.ui.ChatActivity.MESSAGE_TYPE_IMAGE;


public class ChatActivity extends MainActivity
  implements View.OnClickListener,
  ClickListenerChatFirebase,
  SinchService.StartFailedListener {

  private RecyclerView recyclerChat;
  public static final int VIEW_TYPE_USER_MESSAGE_TEXT = 0;
  public static final int VIEW_TYPE_FRIEND_MESSAGE_TEXT = 1;
  public static final int VIEW_TYPE_USER_MESSAGE_IMAGE = 2;
  public static final int VIEW_TYPE_FRIEND_MESSAGE_IMAGE = 3;
  public static final int VIEW_TYPE_USER_MESSAGE_AUDIO = 4;
  public static final int VIEW_TYPE_FRIEND_MESSAGE_AUDIO = 5;

  static final String TAG = ChatActivity.class.getSimpleName();

  public static final String MESSAGE_TYPE_TEXT = "text";
  public static final String MESSAGE_TYPE_IMAGE = "img";
  public static final String MESSAGE_TYPE_AUDIO = "audio";

  private static final int IMAGE_GALLERY_REQUEST = 1;
  private static final int IMAGE_CAMERA_REQUEST = 2;
  private static final int PLACE_PICKER_REQUEST = 3;
  private static final int CONTACT_PICKER_REQUEST = 4;
  private static final int AUDIO_PICKER_REQUEST = 5;
  private static final int DOC_PICKER_REQUEST = 6;

  private File filePathImageCamera;

  private ListMessageAdapter adapter;
  private String roomId;
  private ArrayList<CharSequence> idFriend;
  private Consersation consersation;

  //attach files
  private LinearLayout attach_file_view;
  private ImageButton gallery_btn, photo_btn, video_btn, audio_btn, location_btn, contact_btn;
  private boolean attach_hidden = true;


  //audio recording
  private RecordButton recordButton;
  private MediaRecorder recorder = null;
  // Requesting permission to RECORD_AUDIO
  private boolean permissionToRecordAccepted = false;
  private String[] permissions = {Manifest.permission.RECORD_AUDIO};
  private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
  private static String mFileName = null;

  //audio playing
  private MediaPlayer player;
  private String SAVED_URL_PLAY;
  private ImageView SAVED_PLAY_AUDIO;
  private ImageView SAVED_PAUSE_AUDIO;


  private EditText editWriteMessage;
  private LinearLayoutManager linearLayoutManager;
  public static HashMap<String, Bitmap> bitmapAvataFriend;

  public Bitmap bitmapAvataUser;

  private Context context;


  FirebaseStorage storage = FirebaseStorage.getInstance();

  MenuItem audio_call;
  MenuItem video_call;
  private boolean audioVideoServiceConnected = false;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_chat, menu);

    audio_call = menu.getItem(1);
    video_call = menu.getItem(2);

    if (audioVideoServiceConnected) {
      audio_call.setEnabled(true);
      video_call.setEnabled(true);
    }

    return true;
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_chat);
    context = this;
    Intent intentData = getIntent();
    idFriend = intentData.getCharSequenceArrayListExtra(INTENT_KEY_CHAT_ID);
    roomId = intentData.getStringExtra(StaticConfig.INTENT_KEY_CHAT_ROOM_ID);
    String nameFriend = intentData.getStringExtra(StaticConfig.INTENT_KEY_CHAT_FRIEND);

    consersation = new Consersation();


    String base64AvataUser = SharedPreferenceHelper.getInstance(this).getUserInfo().avata;
    if (!base64AvataUser.equals(StaticConfig.STR_DEFAULT_BASE64)) {
      byte[] decodedString = Base64.decode(base64AvataUser, Base64.DEFAULT);
      bitmapAvataUser = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
    } else {
      bitmapAvataUser = null;
    }

    initAttachFileButtons();
    initAudioButtons();
    initAudioRecord();


    editWriteMessage = (EditText) findViewById(R.id.editWriteMessage);
    initEditText(nameFriend);
  }

  private void initAttachFileButtons() {
    attach_file_view = (LinearLayout) findViewById(R.id.reveal_items);
    attach_file_view.setVisibility(View.INVISIBLE);

    gallery_btn = (ImageButton) findViewById(R.id.gallery_img_btn);
    photo_btn = (ImageButton) findViewById(R.id.photo_img_btn);
    video_btn = (ImageButton) findViewById(R.id.video_img_btn);
    audio_btn = (ImageButton) findViewById(R.id.audio_img_btn);
    location_btn = (ImageButton) findViewById(R.id.location_img_btn);
    contact_btn = (ImageButton) findViewById(R.id.contact_img_btn);

    gallery_btn.setOnClickListener(this);
    photo_btn.setOnClickListener(this);
    video_btn.setOnClickListener(this);
    audio_btn.setOnClickListener(this);
    location_btn.setOnClickListener(this);
    contact_btn.setOnClickListener(this);
  }


  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode) {
      case REQUEST_RECORD_AUDIO_PERMISSION:
        permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        break;
    }
    if (!permissionToRecordAccepted) finish();

    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
    } else {

    }

  }

  private void initAudioRecord() {
    // Record to the external cache directory for visibility
    mFileName = getExternalCacheDir() != null ? getExternalCacheDir().getAbsolutePath() : "";
    mFileName += "/audioMessage.3gp";

    ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
  }


  private void initAudioButtons() {
    //audio record
    RecordView recordView = (RecordView) findViewById(R.id.record_view);
    recordButton = (RecordButton) findViewById(R.id.record_button);

    //IMPORTANT
    recordButton.setRecordView(recordView);

    recordView.setCancelBounds(130);

    recordButton.setOnRecordClickListener(new OnRecordClickListener() {
      @Override
      public void onClick(View v) {
        sendTextMessage();
        Log.d("RecordButton", "RECORD BUTTON CLICKED");
      }
    });

    recordView.setOnBasketAnimationEndListener(new OnBasketAnimationEnd() {
      @Override
      public void onAnimationEnd() {
        Log.d("RecordView", "Basket Animation Finished");
      }
    });


    recordView.setOnRecordListener(new OnRecordListener() {
      @Override
      public void onStart() {
        //Start Recording..
        Log.d("RecordView", "onStart");

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(mFileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
          recorder.prepare();
        } catch (IOException e) {
          Log.e("RECORDING AUDIO FAILED", "prepare() failed");
        }

        recorder.start();
      }

      @Override
      public void onCancel() {

        try {
          recorder.stop();
          recorder.reset();
          recorder = null;
        } catch (Exception e) {
          recorder = null;
          Log.w("stop recrod", e);
        }


        //On Swipe To Cancel
        Log.d("RecordView", "onCancel");

      }

      @Override
      public void onFinish(long recordTime) {
        //Stop Recording..
        System.out.println(recordTime);
        Log.d("RecordView", "onFinish");

        Log.d("RecordTime", String.valueOf(recordTime));

        recorder.stop();
        recorder.release();
        recorder = null;


        sendAudioFirebase(mFileName);
//          player.setDataSource(mFileName);
//          player.prepare();
//          player.start();

      }

      @Override
      public void onLessThanSecond() {
        onCancel();
        //When the record time is less than One Second
        Log.d("RecordView", "onLessThanSecond");
      }
    });
  }

  private void initEditText(String nameFriend) {
    if (idFriend != null && nameFriend != null) {
      getSupportActionBar().setTitle(nameFriend);


      linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
      recyclerChat = (RecyclerView) findViewById(R.id.recyclerChat);
      recyclerChat.setLayoutManager(linearLayoutManager);
      adapter = new ListMessageAdapter(this, consersation, bitmapAvataFriend, bitmapAvataUser, this);
      FirebaseDatabase.getInstance().getReference().child("message/" + roomId).addChildEventListener(new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
          if (dataSnapshot.getValue() != null) {
            HashMap mapMessage = (HashMap) dataSnapshot.getValue();

            Message newMessage = new Message();
            newMessage.idSender = (String) mapMessage.get("idSender");
            newMessage.idReceiver = (String) mapMessage.get("idReceiver");
            newMessage.timestamp = (long) mapMessage.get("timestamp");

            if (mapMessage.get("text") != null) {
              newMessage.text = (String) mapMessage.get("text");
            }


            if (mapMessage.get("fileModel") != null) {
              FileModel fileModel = new FileModel();
              HashMap fileHash = (HashMap) mapMessage.get("fileModel");
              if (fileHash.containsKey("type"))
                fileModel.type = (String) fileHash.get("type");

              if (fileHash.containsKey("name_file"))
                fileModel.name_file = (String) fileHash.get("name_file");

              if (fileHash.containsKey("url_file"))
                fileModel.url_file = (String) fileHash.get("url_file");

              newMessage.fileModel = fileModel;
            }

            consersation.getListMessageData().add(newMessage);
            adapter.notifyDataSetChanged();
            linearLayoutManager.scrollToPosition(consersation.getListMessageData().size() - 1);
          }
        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String s) {

        }

        @Override
        public void onChildRemoved(DataSnapshot dataSnapshot) {

        }

        @Override
        public void onChildMoved(DataSnapshot dataSnapshot, String s) {

        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
      });
      recyclerChat.setAdapter(adapter);
    }

    editWriteMessage.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {

      }

      @Override
      public void afterTextChanged(Editable s) {
        if (editWriteMessage != null && editWriteMessage.getText().toString().trim().length() == 0) {
          recordButton.setListenForRecord(true);
          recordButton.setImageResource(R.drawable.record);
        } else {
          recordButton.setListenForRecord(false);
          recordButton.setImageResource(R.drawable.send);
        }
      }
    });
  }


  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {

    if (requestCode == IMAGE_GALLERY_REQUEST) {
      if (resultCode == RESULT_OK) {
        Uri selectedImageUri = data.getData();
        if (selectedImageUri != null) {

          sendImageFirebase(selectedImageUri);

        }
      }
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    switch (item.getItemId()) {
      case R.id.about: {
        Toast.makeText(context, "Version 1.0", Toast.LENGTH_LONG).show();
        break;
      }
      case R.id.audio_call: {
        audioCall();
        break;
      }
      case R.id.video_call: {
        videoCall();
        break;
      }
      case android.R.id.home: {
        Intent result = new Intent();
        result.putExtra("idFriend", idFriend.get(0));
        setResult(RESULT_OK, result);
        this.finish();
        break;
      }

      case R.id.attach_btn:
        openAttachItemMenu();
        break;
    }

    return true;
  }

  private void openAttachItemMenu() {

    int cx = (attach_file_view.getLeft() + attach_file_view.getRight());
    int cy = attach_file_view.getTop();
    int radius = Math.max(attach_file_view.getWidth(), attach_file_view.getHeight());

    //Below Android LOLIPOP
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      SupportAnimator animator =
        ViewAnimationUtils.createCircularReveal(attach_file_view, cx, cy, 0, radius);
      animator.setInterpolator(new AccelerateDecelerateInterpolator());
      animator.setDuration(700);

      SupportAnimator animator_reverse = animator.reverse();

      if (attach_hidden) {
        attach_file_view.setVisibility(View.VISIBLE);
        animator.start();
        attach_hidden = false;
      } else {
        animator_reverse.addListener(new SupportAnimator.AnimatorListener() {
          @Override
          public void onAnimationStart() {

          }

          @Override
          public void onAnimationEnd() {
            attach_file_view.setVisibility(View.INVISIBLE);
            attach_hidden = true;

          }

          @Override
          public void onAnimationCancel() {

          }

          @Override
          public void onAnimationRepeat() {

          }
        });
        animator_reverse.start();
      }
    }
    // Android LOLIPOP And ABOVE
    else {
      if (attach_hidden) {
        Animator anim = android.view.ViewAnimationUtils.createCircularReveal(attach_file_view, cx, cy, 0, radius);
        attach_file_view.setVisibility(View.VISIBLE);
        anim.start();
        attach_hidden = false;
      } else {
        Animator anim = android.view.ViewAnimationUtils.createCircularReveal(attach_file_view, cx, cy, radius, 0);
        anim.addListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            attach_file_view.setVisibility(View.INVISIBLE);
            attach_hidden = true;
          }
        });
        anim.start();
      }
    }
  }

  @Override
  public void onBackPressed() {
    Intent result = new Intent();
    result.putExtra("idFriend", idFriend.get(0));
    setResult(RESULT_OK, result);
    this.finish();
  }

  @Override
  public void onClick(View view) {

    switch (view.getId()) {
      case R.id.audio_img_btn:
        chooseMedia(AUDIO_PICKER_REQUEST);
        break;

      case R.id.photo_img_btn:
        chooseMedia(IMAGE_CAMERA_REQUEST);
        break;

      case R.id.video_img_btn:
        chooseMedia(0);
        break;

      case R.id.contact_img_btn:
        chooseMedia(CONTACT_PICKER_REQUEST);
        break;

      case R.id.gallery_img_btn:
        chooseMedia(IMAGE_GALLERY_REQUEST);
        break;

      case R.id.location_img_btn:
        chooseMedia(PLACE_PICKER_REQUEST);
        break;
    }
  }

  private void audioCall() {

    String userId = (String) idFriend.get(0);
    if (userId.isEmpty()) {
      return;
    }

    try {
      Call call = getSinchServiceInterface().callUser(userId);
      if (call == null) {
        return;
      }
      String callId = call.getCallId();
      Intent callScreen = new Intent(this, CallScreenActivity.class);
      callScreen.putExtra(SinchService.CALL_ID, callId);
      startActivity(callScreen);
    } catch (MissingPermissionException e) {
      ActivityCompat.requestPermissions(this, new String[]{e.getRequiredPermission()}, 0);
    }
  }

  private void videoCall() {

    String userId = (String) idFriend.get(0);
    if (userId.isEmpty()) {
      return;
    }

    try {
      Call call = getSinchServiceInterface().callUserVideo(userId);
      if (call == null) {
        return;
      }
      String callId = call.getCallId();
      Intent callScreen = new Intent(this, CallScreenActivity.class);
      callScreen.putExtra(SinchService.CALL_ID, callId);
      startActivity(callScreen);
    } catch (MissingPermissionException e) {
      ActivityCompat.requestPermissions(this, new String[]{e.getRequiredPermission()}, 0);
    }
  }


  private void sendTextMessage() {
    String content = editWriteMessage.getText().toString().trim();
    if (content.length() > 0) {
      editWriteMessage.setText("");
      Message newMessage = new Message();
      newMessage.text = content;
      newMessage.fileModel = null;
      newMessage.idSender = StaticConfig.UID;
      newMessage.idReceiver = roomId;
      newMessage.timestamp = System.currentTimeMillis();
      FirebaseDatabase.getInstance().getReference().child("message/" + roomId).push().setValue(newMessage);
    }
  }

  private void sendImageFirebase(Uri selectedImageUri) {

    final Message newMessage = new Message();
    newMessage.text = null;
    final String nameOfImage = MESSAGE_TYPE_IMAGE + DateFormat.format("yyyy-MM-dd_hhmmss", new Date()).toString();

    StorageReference imageRef = storage.getReference().child("images/" + selectedImageUri.getLastPathSegment());

    UploadTask uploadTask = imageRef.putFile(selectedImageUri);
    uploadTask.addOnFailureListener(new OnFailureListener() {
      @Override
      public void onFailure(@NonNull Exception e) {
        Log.e(TAG, "onFailure sendFileFirebase " + e.getMessage());
      }
    }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
      @Override
      public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
        FileModel fileModel = new FileModel();
        fileModel.name_file = nameOfImage;
        fileModel.type = MESSAGE_TYPE_IMAGE;

        //TODO CORRECT WAY TO GET URL
        fileModel.url_file = taskSnapshot.getStorage().getDownloadUrl().toString();

        newMessage.fileModel = fileModel;

        newMessage.idSender = StaticConfig.UID;
        newMessage.idReceiver = roomId;
        newMessage.timestamp = System.currentTimeMillis();
        FirebaseDatabase.getInstance().getReference().child("message/" + roomId).push().setValue(newMessage);
      }
    });

  }

  private void sendAudioFirebase(String fileName) {

    final Message newMessage = new Message();
    newMessage.text = null;
    final String nameOfAudio = MESSAGE_TYPE_AUDIO + DateFormat.format("yyyy-MM-dd_hhmmss", new Date()).toString();

    Uri file = Uri.fromFile(new File(fileName));

    StorageReference audioRef = storage.getReference().child("audio/" + nameOfAudio);

    UploadTask uploadTask = audioRef.putFile(file);
    uploadTask.addOnFailureListener(new OnFailureListener() {
      @Override
      public void onFailure(@NonNull Exception e) {
        Log.e(TAG, "onFailure sendFileFirebase " + e.getMessage());
      }
    }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
      @Override
      public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
        FileModel fileModel = new FileModel();
        fileModel.name_file = nameOfAudio;
        fileModel.type = MESSAGE_TYPE_AUDIO;
        fileModel.url_file = taskSnapshot.getDownloadUrl().toString();

        newMessage.fileModel = fileModel;

        newMessage.idSender = StaticConfig.UID;
        newMessage.idReceiver = roomId;
        newMessage.timestamp = System.currentTimeMillis();
        FirebaseDatabase.getInstance().getReference().child("message/" + roomId).push().setValue(newMessage);
      }
    });
  }

  private void chooseMedia(int content) {
    if (content == AUDIO_PICKER_REQUEST) {
      Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.setType("audio/*");
      startActivityForResult(intent, AUDIO_PICKER_REQUEST);
      return;
    }
    if (content == PLACE_PICKER_REQUEST) {

      try {
        PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
        startActivityForResult(builder.build(this), PLACE_PICKER_REQUEST);
        return;
      } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
        e.printStackTrace();
      }
    }

    if (content == IMAGE_CAMERA_REQUEST) {
      String nomeFoto = DateFormat.format("yyyy-MM-dd_hhmmss", new Date()).toString();
      filePathImageCamera = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), nomeFoto + "/camera.jpg");
      Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
      Uri photoURI = FileProvider.getUriForFile(ChatActivity.this,
        BuildConfig.APPLICATION_ID + ".provider",
        filePathImageCamera);
      it.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
      startActivityForResult(it, IMAGE_CAMERA_REQUEST);
      return;
    }

    if (content == IMAGE_GALLERY_REQUEST) {
      Intent intent = new Intent();
      intent.setType("image/*");
      intent.setAction(Intent.ACTION_GET_CONTENT);
      startActivityForResult(Intent.createChooser(intent, ""), IMAGE_GALLERY_REQUEST);
      return;
    }
    if (content == DOC_PICKER_REQUEST) {
      Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.setType("file/*");
      startActivityForResult(intent, DOC_PICKER_REQUEST);

      return;
    }
    if (content == CONTACT_PICKER_REQUEST) {
      Intent pickIntent = new Intent(Intent.ACTION_PICK,
        android.provider.ContactsContract.Contacts.CONTENT_URI);
      startActivityForResult(pickIntent, CONTACT_PICKER_REQUEST);
      return;
    }

  }

  @Override
  public void clickImageChat(View view, int position, String nameUser, String urlPhotoUser, String urlPhotoClick) {

  }

  @Override
  public void clickImageMapChat(View view, int position, String latitude, String longitude) {

  }

  private final Handler handler = new Handler();

  public void startPlayProgressUpdater(final SeekBar seek) {
    if (player == null) return;
    seek.setProgress(player.getCurrentPosition());

    if (player != null && player.isPlaying()) {
      Runnable notification = new Runnable() {
        public void run() {
          startPlayProgressUpdater(seek);
        }
      };
      handler.postDelayed(notification, 10);
    }
  }

  @Override
  public void clickAudioPlayChat(View view, int position, final ImageView play_audio, final ImageView pause_audio, final SeekBar seek) {
    if (consersation.getListMessageData().get(position).fileModel != null &&
      MESSAGE_TYPE_AUDIO.equalsIgnoreCase(consersation.getListMessageData().get(position).fileModel.type)
      && SAVED_URL_PLAY == null && player == null) {
      SAVED_URL_PLAY = consersation.getListMessageData().get(position).fileModel.url_file;
      player = new MediaPlayer();
      try {
        player.setDataSource(consersation.getListMessageData().get(position).fileModel.url_file);
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
          @Override
          public void onPrepared(MediaPlayer mp) {
            seek.setMax(player.getDuration());
            player.seekTo(seek.getProgress());
            player.start();

            startPlayProgressUpdater(seek);
            play_audio.setVisibility(View.GONE);
            pause_audio.setVisibility(View.VISIBLE);

            SAVED_PLAY_AUDIO = play_audio;
            SAVED_PAUSE_AUDIO = pause_audio;
          }
        });

        player.prepare();

      } catch (Exception e) {
        Log.e(TAG, "onFailure play audio " + e.getMessage());
      }

    } else if (SAVED_URL_PLAY != null &&
      SAVED_URL_PLAY.equalsIgnoreCase(consersation.getListMessageData().get(position).fileModel.url_file) &&
      player != null) {
      player.start();
      startPlayProgressUpdater(seek);
      play_audio.setVisibility(View.GONE);
      pause_audio.setVisibility(View.VISIBLE);
    } else if (player != null) {
      player.stop();
      SAVED_PLAY_AUDIO.setVisibility(View.VISIBLE);
      SAVED_PAUSE_AUDIO.setVisibility(View.GONE);
      player = null;
      SAVED_URL_PLAY = null;
      SAVED_PLAY_AUDIO = null;
      SAVED_PAUSE_AUDIO = null;

      clickAudioPlayChat(view, position, play_audio, pause_audio, seek);
    }

    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      @Override
      public void onCompletion(MediaPlayer mp) {
        play_audio.setVisibility(View.VISIBLE);
        pause_audio.setVisibility(View.GONE);
        SAVED_URL_PLAY = null;
        player = null;
        seek.setProgress(0);
      }
    });

  }

  @Override
  public void clickAudioPauseChat(View view, int position, final ImageView play_audio, final ImageView pause_audio, final SeekBar seek) {
    if (consersation.getListMessageData().get(position).fileModel != null &&
      MESSAGE_TYPE_AUDIO.equalsIgnoreCase(consersation.getListMessageData().get(position).fileModel.type) &&
      player != null) {
      player.pause();
      seek.setProgress(player.getCurrentPosition());
      play_audio.setVisibility(View.VISIBLE);
      pause_audio.setVisibility(View.GONE);
    }
  }

  @Override
  public void seekChange(View view, ImageView play_button, ImageView pause_button, SeekBar seekBar) throws Exception {
    if (player != null && player.isPlaying()) {
      player.seekTo(seekBar.getProgress());
    }
  }


  //SINCH
  @Override
  public void onStartFailed(SinchError error) {
  }

  @Override
  public void onStarted() {
  }

  @Override
  protected void onServiceConnected() {
    getSinchServiceInterface().setStartListener(this);

    audioVideoServiceConnected = true;

    if (audio_call != null) {
      audio_call.setEnabled(true);
    }

    if (video_call != null) {
      video_call.setEnabled(true);
    }
  }
}

class ListMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private Context context;
  private Consersation consersation;
  private HashMap<String, Bitmap> bitmapAvata;
  private HashMap<String, DatabaseReference> bitmapAvataDB;
  private Bitmap bitmapAvataUser;
  private ClickListenerChatFirebase clickListenerChatFirebase;

  public ListMessageAdapter(Context context, Consersation consersation, HashMap<String, Bitmap> bitmapAvata, Bitmap bitmapAvataUser, ClickListenerChatFirebase clickListenerChatFirebase) {
    this.context = context;
    this.consersation = consersation;
    this.bitmapAvata = bitmapAvata;
    this.bitmapAvataUser = bitmapAvataUser;
    bitmapAvataDB = new HashMap<>();
    this.clickListenerChatFirebase = clickListenerChatFirebase;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    if (viewType == ChatActivity.VIEW_TYPE_FRIEND_MESSAGE_TEXT) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_friend, parent, false);
      return new ItemMessageFriendHolder(view, clickListenerChatFirebase);
    } else if (viewType == ChatActivity.VIEW_TYPE_USER_MESSAGE_TEXT) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_user, parent, false);
      return new ItemMessageUserHolder(view, clickListenerChatFirebase);
    } else if (viewType == ChatActivity.VIEW_TYPE_USER_MESSAGE_IMAGE) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_user_img, parent, false);
      return new ItemMessageUserHolder(view, clickListenerChatFirebase);
    } else if (viewType == ChatActivity.VIEW_TYPE_FRIEND_MESSAGE_IMAGE) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_friend_img, parent, false);
      return new ItemMessageFriendHolder(view, clickListenerChatFirebase);
    } else if (viewType == ChatActivity.VIEW_TYPE_USER_MESSAGE_AUDIO) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_user_audio, parent, false);
      return new ItemMessageUserHolder(view, clickListenerChatFirebase);
    } else {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_friend_audio, parent, false);
      return new ItemMessageFriendHolder(view, clickListenerChatFirebase);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    if (holder instanceof ItemMessageFriendHolder) {
      if (consersation.getListMessageData().get(position).text != null) {
        if (((ItemMessageFriendHolder) holder).txtContent != null) {
          ((ItemMessageFriendHolder) holder).txtContent.setVisibility(View.VISIBLE);
          ((ItemMessageFriendHolder) holder).txtContent.setText(consersation.getListMessageData().get(position).text);
        }
      } else {
        if (((ItemMessageFriendHolder) holder).txtContent != null)
          ((ItemMessageFriendHolder) holder).txtContent.setVisibility(View.INVISIBLE);
      }
      if (consersation.getListMessageData().get(position).fileModel != null) {

        if (MESSAGE_TYPE_IMAGE.equalsIgnoreCase(consersation.getListMessageData().get(position).fileModel.type)) {
          if (((ItemMessageFriendHolder) holder).imageContent != null) {
            ((ItemMessageFriendHolder) holder).imageContent.setVisibility(View.VISIBLE);

            Glide.with(((ItemMessageFriendHolder) holder).imageContent.getContext())
              .load(consersation.getListMessageData().get(position).fileModel.url_file)
              .into(((ItemMessageFriendHolder) holder).imageContent);
          }
        }

        if (MESSAGE_TYPE_AUDIO.equalsIgnoreCase(consersation.getListMessageData().get(position).fileModel.type)) {
          if (((ItemMessageFriendHolder) holder).audioContent != null) {
            ((ItemMessageFriendHolder) holder).audioContent.setVisibility(View.VISIBLE);
            //TODO AUDIO PLAYER
          }
        }

//        ((ItemMessageUserHolder) holder).imageContent.setImageURI(Uri.parse(consersation.getListMessageData().get(position).fileModel.url_file));
      } else {
        if (((ItemMessageFriendHolder) holder).imageContent != null) {
          ((ItemMessageFriendHolder) holder).imageContent.setVisibility(View.INVISIBLE);
        }

        if (((ItemMessageFriendHolder) holder).audioContent != null) {
          ((ItemMessageFriendHolder) holder).audioContent.setVisibility(View.INVISIBLE);
        }
      }
      Bitmap currentAvata = bitmapAvata.get(consersation.getListMessageData().get(position).idSender);
      if (currentAvata != null) {
        ((ItemMessageFriendHolder) holder).avata.setImageBitmap(currentAvata);
      } else {
        final String id = consersation.getListMessageData().get(position).idSender;
        if (bitmapAvataDB.get(id) == null) {
          bitmapAvataDB.put(id, FirebaseDatabase.getInstance().getReference().child("user/" + id + "/avata"));
          bitmapAvataDB.get(id).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
              if (dataSnapshot.getValue() != null) {
                String avataStr = (String) dataSnapshot.getValue();
                if (!avataStr.equals(StaticConfig.STR_DEFAULT_BASE64)) {
                  byte[] decodedString = Base64.decode(avataStr, Base64.DEFAULT);
                  ChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length));
                } else {
                  ChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avata));
                }
                notifyDataSetChanged();
              }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
          });
        }
      }
    } else if (holder instanceof ItemMessageUserHolder) {
      if (bitmapAvataUser != null) {
        ((ItemMessageUserHolder) holder).avata.setImageBitmap(bitmapAvataUser);
      }


      if (consersation.getListMessageData().get(position).text != null) {
        if (((ItemMessageUserHolder) holder).txtContent != null) {
          ((ItemMessageUserHolder) holder).txtContent.setVisibility(View.VISIBLE);
          ((ItemMessageUserHolder) holder).txtContent.setText(consersation.getListMessageData().get(position).text);
        }
      } else {
        if (((ItemMessageUserHolder) holder).txtContent != null)
          ((ItemMessageUserHolder) holder).txtContent.setVisibility(View.INVISIBLE);
      }
      if (consersation.getListMessageData().get(position).fileModel != null) {

        if (MESSAGE_TYPE_IMAGE.equalsIgnoreCase(consersation.getListMessageData().get(position).fileModel.type)) {
          if (((ItemMessageUserHolder) holder).imageContent != null) {


            ((ItemMessageUserHolder) holder).imageContent.setVisibility(View.VISIBLE);

            Glide.with(((ItemMessageUserHolder) holder).imageContent.getContext())
              .load(consersation.getListMessageData().get(position).fileModel.url_file)
              .into(((ItemMessageUserHolder) holder).imageContent);
          }
        }

        if (MESSAGE_TYPE_AUDIO.equalsIgnoreCase(consersation.getListMessageData().get(position).fileModel.type)) {
          if (((ItemMessageUserHolder) holder).audioContent != null) {
            ((ItemMessageUserHolder) holder).audioContent.setVisibility(View.VISIBLE);
            if (((ItemMessageUserHolder) holder).totalTime != null) {
              ((ItemMessageUserHolder) holder).totalTime.setText("test");
            }
            if (((ItemMessageUserHolder) holder).dateTime != null) {
              ((ItemMessageUserHolder) holder).dateTime.setText(String.valueOf(consersation.getListMessageData().get(position).timestamp));
            }

            //TODO AUDIO PLAYER
          }
        }


//        ((ItemMessageUserHolder) holder).imageContent.setImageURI(Uri.parse(consersation.getListMessageData().get(position).fileModel.url_file));
      } else {
        if (((ItemMessageUserHolder) holder).imageContent != null)
          ((ItemMessageUserHolder) holder).imageContent.setVisibility(View.INVISIBLE);
      }
    }
  }

  @Override
  public int getItemViewType(int position) {
    if (consersation.getListMessageData().get(position).text == null && consersation.getListMessageData().get(position).fileModel != null) {

      if (MESSAGE_TYPE_IMAGE.equals(consersation.getListMessageData().get(position).fileModel.type)) {
        return consersation.getListMessageData().get(position).idSender.equals(StaticConfig.UID)
          ? ChatActivity.VIEW_TYPE_USER_MESSAGE_IMAGE : ChatActivity.VIEW_TYPE_FRIEND_MESSAGE_IMAGE;
      }

      if (MESSAGE_TYPE_AUDIO.equals(consersation.getListMessageData().get(position).fileModel.type)) {
        return consersation.getListMessageData().get(position).idSender.equals(StaticConfig.UID)
          ? ChatActivity.VIEW_TYPE_USER_MESSAGE_AUDIO : ChatActivity.VIEW_TYPE_FRIEND_MESSAGE_AUDIO;
      }
    }

    return consersation.getListMessageData().get(position).idSender.equals(StaticConfig.UID)
      ? ChatActivity.VIEW_TYPE_USER_MESSAGE_TEXT : ChatActivity.VIEW_TYPE_FRIEND_MESSAGE_TEXT;

  }

  @Override
  public int getItemCount() {
    return consersation.getListMessageData().size();
  }

}

class ItemMessageUserHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnTouchListener {
  public TextView txtContent;
  public ImageView imageContent;
  public ImageView play_audio;
  public ImageView pause_audio;
  public LinearLayout audioContent;
  public CircleImageView avata;

  public TextView totalTime;
  public TextView dateTime;

  public SeekBar user_seekbar;

  private ClickListenerChatFirebase clickListenerChatFirebase;

  public ItemMessageUserHolder(View itemView, ClickListenerChatFirebase clickListenerChatFirebase) {
    super(itemView);
    txtContent = (TextView) itemView.findViewById(R.id.textContentUser);
    imageContent = (ImageView) itemView.findViewById(R.id.imageContentUser);
    audioContent = (LinearLayout) itemView.findViewById(R.id.audioUserView);
    play_audio = (ImageView) itemView.findViewById(R.id.play_audio);
    pause_audio = (ImageView) itemView.findViewById(R.id.pause_audio);
    user_seekbar = (SeekBar) itemView.findViewById(R.id.user_seekbar);
    avata = (CircleImageView) itemView.findViewById(R.id.imageView2);

    totalTime = (TextView) itemView.findViewById(R.id.total_time);
    dateTime = (TextView) itemView.findViewById(R.id.date_time);

    this.clickListenerChatFirebase = clickListenerChatFirebase;

    if (play_audio != null) {
      play_audio.setOnClickListener(this);
    }
    if (pause_audio != null) {
      pause_audio.setOnClickListener(this);
    }

    if (user_seekbar != null) {
      user_seekbar.setOnTouchListener(this);
    }
  }

  @Override
  public void onClick(View v) {
    int position = getAdapterPosition();
    System.out.println("ID " + v.getId());
    switch (v.getId()) {
      case R.id.play_audio:
        try {
          clickListenerChatFirebase.clickAudioPlayChat(v, position, play_audio, pause_audio, user_seekbar);
        } catch (Exception e) {
          e.printStackTrace();
        }
        break;
      case R.id.pause_audio:
        try {
          clickListenerChatFirebase.clickAudioPauseChat(v, position, play_audio, pause_audio, user_seekbar);
        } catch (Exception e) {
          e.printStackTrace();
        }
        break;

    }
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    switch (v.getId()) {
      case R.id.user_seekbar: {
        try {
          clickListenerChatFirebase.seekChange(v, play_audio, pause_audio, user_seekbar);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    return false;
  }
}

class ItemMessageFriendHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnTouchListener {
  public TextView txtContent;
  public CircleImageView avata;
  public ImageView imageContent;
  public LinearLayout audioContent;
  public ImageView play_audio;
  public ImageView pause_audio;

  public TextView totalTime;
  public TextView dateTime;

  public SeekBar friend_seekbar;

  private ClickListenerChatFirebase clickListenerChatFirebase;

  public ItemMessageFriendHolder(View itemView, ClickListenerChatFirebase clickListenerChatFirebase) {
    super(itemView);
    txtContent = (TextView) itemView.findViewById(R.id.textContentFriend);
    imageContent = (ImageView) itemView.findViewById(R.id.imageContentFriend);
    audioContent = (LinearLayout) itemView.findViewById(R.id.audioFriendView);
    play_audio = (ImageView) itemView.findViewById(R.id.play_audio);
    pause_audio = (ImageView) itemView.findViewById(R.id.pause_audio);
    friend_seekbar = (SeekBar) itemView.findViewById(R.id.friend_seekbar);
    avata = (CircleImageView) itemView.findViewById(R.id.imageView3);

    totalTime = (TextView) itemView.findViewById(R.id.total_time);
    dateTime = (TextView) itemView.findViewById(R.id.date_time);
    this.clickListenerChatFirebase = clickListenerChatFirebase;

    if (play_audio != null) {
      play_audio.setOnClickListener(this);
    }
    if (pause_audio != null) {
      pause_audio.setOnClickListener(this);
    }

    if (friend_seekbar != null) {
      friend_seekbar.setOnTouchListener(this);
    }

  }

  @Override
  public void onClick(View v) {
    int position = getAdapterPosition();
    System.out.println("ID " + v.getId());
    switch (v.getId()) {
      case R.id.play_audio:
        try {
          clickListenerChatFirebase.clickAudioPlayChat(v, position, play_audio, pause_audio, friend_seekbar);
        } catch (Exception e) {
          e.printStackTrace();
        }
        break;
      case R.id.pause_audio:
        try {
          clickListenerChatFirebase.clickAudioPauseChat(v, position, play_audio, pause_audio, friend_seekbar);
        } catch (Exception e) {
          e.printStackTrace();
        }
        break;

    }
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    switch (v.getId()) {
      case R.id.user_seekbar: {
        try {
          clickListenerChatFirebase.seekChange(v, play_audio, pause_audio, friend_seekbar);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    return false;
  }
}
