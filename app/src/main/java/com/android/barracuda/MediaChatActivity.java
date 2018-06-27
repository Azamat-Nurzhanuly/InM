package com.android.barracuda;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.barracuda.data.SharedPreferenceHelper;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.inter.ClickListenerChatFirebase;
import com.android.barracuda.model.Consersation;
import com.android.barracuda.model.ContactModel;
import com.android.barracuda.model.FileModel;
import com.android.barracuda.model.Message;
import com.android.barracuda.ui.ImageViewer;
import com.android.barracuda.ui.VideoViewer;
import com.bumptech.glide.Glide;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.yarolegovich.lovelydialog.LovelyProgressDialog;

import java.io.File;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import de.hdodenhof.circleimageview.CircleImageView;

import static com.android.barracuda.R.color.colorMarked;
import static com.android.barracuda.data.StaticConfig.INTENT_KEY_CHAT_ID;


public class MediaChatActivity extends MainActivity
  implements ClickListenerChatFirebase {

  private RecyclerView recyclerChat;

  public static final int VIEW_TYPE_USER_MESSAGE_IMAGE = 2;
  public static final int VIEW_TYPE_FRIEND_MESSAGE_IMAGE = 3;
  public static final int VIEW_TYPE_USER_MESSAGE_AUDIO = 4;
  public static final int VIEW_TYPE_FRIEND_MESSAGE_AUDIO = 5;

  public static final int VIEW_TYPE_USER_MESSAGE_VIDEO = 6;
  public static final int VIEW_TYPE_FRIEND_MESSAGE_VIDEO = 7;

  public static final int VIEW_TYPE_MESSAGE_DATE = 8;

  static final String TAG = MediaChatActivity.class.getSimpleName();

  public static final String MESSAGE_TYPE_IMAGE = "img";
  public static final String MESSAGE_TYPE_AUDIO = "audio";
  public static final String MESSAGE_TYPE_VIDEO = "video";

  public Map<String, Integer> messageMap = new HashMap<>();
  public Map<Integer, Message> favoriteMessages = new HashMap<>();
  public Map<String, View> messageViews = new HashMap<>();

  private File filePathImageCamera;

  private ListMessageAdapter adapter;
  private String roomId;
  private ArrayList<CharSequence> idFriend;
  private Consersation consersation;
  private String nameFriend;
  public Set<Message> totalFavoriteMessages = new HashSet<>();

  private static final SimpleDateFormat d_m_y_formatter = new SimpleDateFormat(
    "yyyy-MMMM-dd, EEEE");

  private static String mFileName = null;

  //audio playing
  private MediaPlayer player;
  private String SAVED_URL_PLAY;
  private ImageView SAVED_PLAY_AUDIO;
  private ImageView SAVED_PAUSE_AUDIO;

  private LinearLayoutManager linearLayoutManager;
  public static HashMap<String, Bitmap> bitmapAvataFriend;

  public Bitmap bitmapAvataUser;

  private Context context;

  FirebaseStorage storage = FirebaseStorage.getInstance();

  MenuItem favorite;
  private LovelyProgressDialog dialogWaitDeleting;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_media_chat, menu);

    favorite = menu.getItem(0);

    return true;
  }

  public RelativeLayout itemMessage;

  @RequiresApi(api = Build.VERSION_CODES.M)
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_media_chat);
    context = this;
    Intent intentData = getIntent();
    idFriend = intentData.getCharSequenceArrayListExtra(INTENT_KEY_CHAT_ID);
    roomId = intentData.getStringExtra(StaticConfig.INTENT_KEY_CHAT_ROOM_ID);
    nameFriend = intentData.getStringExtra(StaticConfig.INTENT_KEY_CHAT_FRIEND);

    dialogWaitDeleting = new LovelyProgressDialog(this);

    consersation = new Consersation();
    messageMap = new HashMap<>();
    favoriteMessages = new HashMap<>();
    totalFavoriteMessages = new HashSet<>();

    int background = getBackground(this);
    View wallpaper = findViewById(R.id.wallpaper);
    wallpaper.setBackgroundResource(background);

    String base64AvataUser = SharedPreferenceHelper.getInstance(this).getUserInfo().avata;
    if (!base64AvataUser.equals(StaticConfig.STR_DEFAULT_BASE64)) {
      byte[] decodedString = Base64.decode(base64AvataUser, Base64.DEFAULT);
      bitmapAvataUser = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
    } else {
      bitmapAvataUser = null;
    }

    initEditText(nameFriend);

    itemMessage = (RelativeLayout) findViewById(R.id.layout2);

    initFavoriteMes();
  }

  private void initFavoriteMes() {

    FirebaseDatabase.getInstance().getReference().child("favorites/" + StaticConfig.UID).addChildEventListener(new ChildEventListener() {
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

          totalFavoriteMessages.add(newMessage);
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
  }

  private void initEditText(String nameFriend) {
    if (idFriend != null && nameFriend != null) {
      Objects.requireNonNull(getSupportActionBar()).setTitle(nameFriend);

      linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
      recyclerChat = (RecyclerView) findViewById(R.id.recyclerChat);
      recyclerChat.setLayoutManager(linearLayoutManager);

      if (consersation != null && consersation.getListMessageData().size() > 0
        && (consersation.getListMessageData().get(consersation.getListMessageData().size() - 1).date != null
        && !Objects.equals(consersation.getListMessageData().get(consersation.getListMessageData().size() - 1).date, ""))) {

        consersation.getListMessageData().remove(consersation.getListMessageData().size() - 1);
      }

      adapter = new ListMessageAdapter(this, consersation, bitmapAvataFriend, bitmapAvataUser, this, this);
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

            if (mapMessage.get("contact") != null) {
              ContactModel contact = new ContactModel();
              HashMap fileHash = (HashMap) mapMessage.get("contact");
              if (fileHash.containsKey("number"))
                contact.number = (String) fileHash.get("number");

              if (fileHash.containsKey("name"))
                contact.name = (String) fileHash.get("name");

              newMessage.contact = contact;
            }

            if (mapMessage.get("incognito") != null && !Objects.equals(newMessage.idSender, StaticConfig.UID)) {

              Boolean incognito = (Boolean) mapMessage.get("incognito");
              newMessage.incognito = incognito;
            }

            if (newMessage.fileModel != null) {
              consersation.getListMessageData().add(newMessage);
            }

            {
              String date = d_m_y_formatter.format(newMessage.timestamp);
              String currentDate = d_m_y_formatter.format(new Timestamp(System.currentTimeMillis()));

              Message dateMessage = new Message();
              dateMessage.date = date;

              if (Objects.equals(date, currentDate)) {
                dateMessage.date = "Сегодня";
              }

              boolean exist = false;
              for (int i = 0; i < consersation.getListMessageData().size(); i++) {
                if (consersation.getListMessageData().get(i).date != null
                  && consersation.getListMessageData().get(i).date.equals(date) || "Сегодня".equals(consersation.getListMessageData().get(i).date)) {
                  exist = true;
                }
              }
              if (!exist) {

                if (consersation.getListMessageData().size() > 0
                  && consersation.getListMessageData().get(consersation.getListMessageData().size() - 1).date != null
                  && !Objects.equals(consersation.getListMessageData().get(consersation.getListMessageData().size() - 1).date, "")) {

                  consersation.getListMessageData().remove(consersation.getListMessageData().size() - 1);
                }
                consersation.getListMessageData().add(dateMessage);
              }
            }

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
  }

  public void onFriendImgClick(View view) {

    Intent profIntent = new Intent(this, FriendProfileActivity.class);
    profIntent.putExtra("friend_id", idFriend.get(0));
    startActivity(profIntent);
  }

  @SuppressLint("ResourceAsColor")
  public void onMessageMark(View view) {

    Integer position = messageMap.get(view.toString().substring(30, 38));

    if (position == null) return;

    Message message = consersation.getListMessageData().get(position);

    if (message == null) return;

    switch (view.getBackground().getAlpha()) {
      case 0: {
        view.setBackgroundColor(colorMarked);

        favoriteMessages.put(position, message);
        break;
      }
      default: {
        view.setBackgroundColor(Color.argb(0, 255, 255, 255));

        for (Iterator<Map.Entry<Integer, Message>> it = favoriteMessages.entrySet().iterator(); it.hasNext(); ) {
          Map.Entry<Integer, Message> entry = it.next();
          if (Objects.equals(entry.getKey(), position)) {
            it.remove();
          }
        }

        break;
      }
    }

    if (favoriteMessages.size() > 0) {

      if (favorite != null) {
        favorite.setEnabled(true);
        favorite.setVisible(true);
      }

    } else {

      if (favorite != null) {
        favorite.setEnabled(false);
        favorite.setVisible(false);
      }
    }
  }

  public void onBackgroundClick(View view) {

    switch (view.getId()) {
      case R.id.wallpaper1: {
        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putInt(SharedPreferenceHelper.SHARE_WALLPAPER, R.drawable.wallpapers1).commit();
        break;
      }
      case R.id.wallpaper2: {
        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putInt(SharedPreferenceHelper.SHARE_WALLPAPER, R.drawable.wallpapers2).commit();
        break;
      }
      case R.id.wallpaper3: {
        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putInt(SharedPreferenceHelper.SHARE_WALLPAPER, R.drawable.wallpapers3).commit();
        break;
      }
      case R.id.wallpaper4: {
        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putInt(SharedPreferenceHelper.SHARE_WALLPAPER, R.drawable.wallpapers4).commit();
        break;
      }
      case R.id.wallpaper5: {
        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putInt(SharedPreferenceHelper.SHARE_WALLPAPER, R.drawable.wallpapers5).commit();
        break;
      }
      case R.id.wallpaper6: {
        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putInt(SharedPreferenceHelper.SHARE_WALLPAPER, R.drawable.wallpapers6).commit();
        break;
      }
      case R.id.wallpaper7: {
        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putInt(SharedPreferenceHelper.SHARE_WALLPAPER, R.drawable.wallpapers7).commit();
        break;
      }
      case R.id.wallpaper8: {
        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putInt(SharedPreferenceHelper.SHARE_WALLPAPER, R.drawable.wallpapers8).commit();
        break;
      }
      case R.id.wallpaper9: {
        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putInt(SharedPreferenceHelper.SHARE_WALLPAPER, R.drawable.wallpapers9).commit();
        break;
      }
    }

    int background = getBackground(this);
    View wallpaper = findViewById(R.id.wallpaper);
    wallpaper.setBackgroundResource(background);
  }

  public static int getBackground(Activity activity) {

    SharedPreferences sharedPreferences = activity.getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
    int wallpaper = sharedPreferences.getInt(SharedPreferenceHelper.SHARE_WALLPAPER, -1);

    if (wallpaper == -1) {

      wallpaper = R.drawable.watermark;
    }

    return wallpaper;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    switch (item.getItemId()) {
      case android.R.id.home: {
        Intent result = new Intent();
        result.putExtra("idFriend", idFriend.get(0));
        result.putExtra(StaticConfig.INTENT_KEY_CHAT_FRIEND, nameFriend);
        ArrayList<CharSequence> idFriend = new ArrayList<CharSequence>();
        idFriend.add(this.idFriend.get(0));
        result.putCharSequenceArrayListExtra(StaticConfig.INTENT_KEY_CHAT_ID, idFriend);
        result.putExtra(StaticConfig.INTENT_KEY_CHAT_ROOM_ID, roomId);
        setResult(RESULT_OK, result);

        this.finish();
        break;
      }
      case R.id.addFavorites: {

        FirebaseDatabase.getInstance().getReference().child("favorites").child(StaticConfig.UID).removeValue();

        for (Iterator<Map.Entry<Integer, Message>> it = favoriteMessages.entrySet().iterator(); it.hasNext(); ) {
          Map.Entry<Integer, Message> entry = it.next();

          if (entry.getValue().incognito != null || !entry.getValue().incognito) {
            totalFavoriteMessages.add(entry.getValue());
          }
        }

        for (Message message : totalFavoriteMessages) {

          FirebaseDatabase.getInstance().getReference().child("favorites").child(StaticConfig.UID).push().setValue(message);
        }

        favoriteMessages.clear();
        for (Iterator<Map.Entry<String, View>> it = messageViews.entrySet().iterator(); it.hasNext(); ) {
          Map.Entry<String, View> entry = it.next();
          entry.getValue().setBackgroundColor(Color.argb(0, 255, 255, 255));
        }

        if (favorite != null) {
          favorite.setEnabled(false);
          favorite.setVisible(false);
        }

        break;
      }
    }

    return true;
  }

  @Override
  public void onBackPressed() {
    Intent result = new Intent();
    result.putExtra("idFriend", idFriend.get(0));
    setResult(RESULT_OK, result);
    this.finish();
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
  public void clickImageChat(View view, int position) {
    if (consersation.getListMessageData().get(position).fileModel != null &&
      MESSAGE_TYPE_IMAGE.equalsIgnoreCase(
        consersation
          .getListMessageData()
          .get(position).fileModel.type)) {
      String url = consersation.getListMessageData().get(position).fileModel.url_file;

      Intent intent = new Intent(context, ImageViewer.class);
      intent.putExtra(StaticConfig.IMAGE_URL, url);
      intent.putExtra(StaticConfig.INTENT_KEY_CHAT_FRIEND, nameFriend);
      startActivity(intent);

    }
  }

  @Override
  public void clickVideoChat(View view, int position) {
    if (consersation.getListMessageData().get(position).fileModel != null &&
      MESSAGE_TYPE_VIDEO.equalsIgnoreCase(
        consersation
          .getListMessageData()
          .get(position).fileModel.type)) {


      String url = consersation.getListMessageData().get(position).fileModel.url_file;

      Intent intent = new Intent(context, VideoViewer.class);
      intent.putExtra(StaticConfig.VIDEO_URL, url);
      intent.putExtra(StaticConfig.VIDEO, nameFriend);
      startActivity(intent);
    }
  }

  @Override
  public void clickImageMapChat(View view, int position, String latitude, String longitude) {

  }

  @Override
  public void seekChange(View view, ImageView play_button, ImageView pause_button, SeekBar seekBar) throws Exception {
    if (player != null && player.isPlaying()) {
      player.seekTo(seekBar.getProgress());
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
  private MediaChatActivity activity;

  public ListMessageAdapter(Context context, Consersation consersation, HashMap<String, Bitmap> bitmapAvata, Bitmap bitmapAvataUser, ClickListenerChatFirebase clickListenerChatFirebase, MediaChatActivity activity) {
    this.context = context;
    this.consersation = consersation;
    this.bitmapAvata = bitmapAvata;
    this.bitmapAvataUser = bitmapAvataUser;
    bitmapAvataDB = new HashMap<>();
    this.clickListenerChatFirebase = clickListenerChatFirebase;
    this.activity = activity;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

    if (viewType == MediaChatActivity.VIEW_TYPE_MESSAGE_DATE) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_date, parent, false);
      return new ItemMessageDate(view);
    }

    if (viewType == MediaChatActivity.VIEW_TYPE_USER_MESSAGE_IMAGE) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_user_img, parent, false);
      return new ItemMediaUserHolder(view, clickListenerChatFirebase);
    } else if (viewType == MediaChatActivity.VIEW_TYPE_FRIEND_MESSAGE_IMAGE) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_friend_img, parent, false);
      return new ItemMediaFriendHolder(view, clickListenerChatFirebase);
    } else if (viewType == MediaChatActivity.VIEW_TYPE_USER_MESSAGE_VIDEO) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_user_video, parent, false);
      return new ItemMediaUserHolder(view, clickListenerChatFirebase);
    } else if (viewType == MediaChatActivity.VIEW_TYPE_FRIEND_MESSAGE_VIDEO) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_friend_video, parent, false);
      return new ItemMediaFriendHolder(view, clickListenerChatFirebase);
    } else if (viewType == MediaChatActivity.VIEW_TYPE_USER_MESSAGE_AUDIO) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_user_audio, parent, false);
      return new ItemMediaUserHolder(view, clickListenerChatFirebase);
    } else if (viewType == MediaChatActivity.VIEW_TYPE_FRIEND_MESSAGE_AUDIO) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_friend_audio, parent, false);
      return new ItemMediaFriendHolder(view, clickListenerChatFirebase);
    }

    throw new UnsupportedOperationException();
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

    this.activity.messageMap.put(holder.itemView.toString().substring(30, 38), position);
    this.activity.messageViews.put(holder.itemView.toString().substring(30, 38), holder.itemView);

    if (holder instanceof ItemMediaFriendHolder) {
      if (consersation.getListMessageData().get(position).text != null) {
        if (((ItemMediaFriendHolder) holder).txtContent != null) {
          ((ItemMediaFriendHolder) holder).txtContent.setVisibility(View.VISIBLE);
          ((ItemMediaFriendHolder) holder).txtContent.setText(consersation.getListMessageData().get(position).text);
        }
      } else {
        if (((ItemMediaFriendHolder) holder).txtContent != null)
          ((ItemMediaFriendHolder) holder).txtContent.setVisibility(View.INVISIBLE);
      }
      if (consersation.getListMessageData().get(position).fileModel != null) {

        if (MediaChatActivity.MESSAGE_TYPE_IMAGE.equalsIgnoreCase(consersation.getListMessageData().get(position).fileModel.type)) {
          if (((ItemMediaFriendHolder) holder).imageContent != null) {
            ((ItemMediaFriendHolder) holder).imageContent.setVisibility(View.VISIBLE);

            Glide.with(((ItemMediaFriendHolder) holder).imageContent.getContext())
              .load(consersation.getListMessageData().get(position).fileModel.url_file)
              .into(((ItemMediaFriendHolder) holder).imageContent);
          }
        }

        if (MediaChatActivity.MESSAGE_TYPE_VIDEO.equalsIgnoreCase(consersation.getListMessageData().get(position).fileModel.type)) {
          if (((ItemMediaFriendHolder) holder).videoContent != null) {


            ((ItemMediaFriendHolder) holder).videoContent.setVisibility(View.VISIBLE);

          }
        }

        if (MediaChatActivity.MESSAGE_TYPE_AUDIO.equalsIgnoreCase(consersation.getListMessageData().get(position).fileModel.type)) {
          if (((ItemMediaFriendHolder) holder).audioContent != null) {
            ((ItemMediaFriendHolder) holder).audioContent.setVisibility(View.VISIBLE);
            //TODO AUDIO PLAYER
          }
        }

//        ((ItemMediaUserHolder) holder).imageContent.setImageURI(Uri.parse(consersation.getListMessageData().get(position).fileModel.url_file));
      } else {
        if (((ItemMediaFriendHolder) holder).imageContent != null) {
          ((ItemMediaFriendHolder) holder).imageContent.setVisibility(View.INVISIBLE);
        }

        if (((ItemMediaFriendHolder) holder).audioContent != null) {
          ((ItemMediaFriendHolder) holder).audioContent.setVisibility(View.INVISIBLE);
        }

        if (consersation.getListMessageData().get(position).contact != null) {
          if (consersation.getListMessageData().get(position).contact.name != null)
            ((ItemMediaFriendHolder) holder).contactName.setText(String.valueOf(consersation.getListMessageData().get(position).contact.name));

          if (consersation.getListMessageData().get(position).contact.number != null)
            ((ItemMediaFriendHolder) holder).contactPhone.setText(String.valueOf(consersation.getListMessageData().get(position).contact.number));
        }

      }
      Bitmap currentAvata = null;
      if (consersation.getListMessageData().get(position) != null && bitmapAvata != null) {
        bitmapAvata.get(consersation.getListMessageData().get(position).idSender);
      }
      if (currentAvata != null) {
        ((ItemMediaFriendHolder) holder).avata.setImageBitmap(currentAvata);
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
                  if (MediaChatActivity.bitmapAvataFriend != null) {
                    MediaChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length));
                  }
                } else {
                  if (MediaChatActivity.bitmapAvataFriend != null) {
                    MediaChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avata));
                  }
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


    } else if (holder instanceof ItemMediaUserHolder) {
      if (bitmapAvataUser != null) {
        ((ItemMediaUserHolder) holder).avata.setImageBitmap(bitmapAvataUser);
      }


      if (consersation.getListMessageData().get(position).text != null) {
        if (((ItemMediaUserHolder) holder).txtContent != null) {
          ((ItemMediaUserHolder) holder).txtContent.setVisibility(View.VISIBLE);
          ((ItemMediaUserHolder) holder).txtContent.setText(consersation.getListMessageData().get(position).text);
        }
      } else {
        if (((ItemMediaUserHolder) holder).txtContent != null)
          ((ItemMediaUserHolder) holder).txtContent.setVisibility(View.INVISIBLE);
      }
      if (consersation.getListMessageData().get(position).fileModel != null) {

        if (MediaChatActivity.MESSAGE_TYPE_IMAGE.equalsIgnoreCase(consersation.getListMessageData().get(position).fileModel.type)) {
          if (((ItemMediaUserHolder) holder).imageContent != null) {


            ((ItemMediaUserHolder) holder).imageContent.setVisibility(View.VISIBLE);

            Glide.with(((ItemMediaUserHolder) holder).imageContent.getContext())
              .load(consersation.getListMessageData().get(position).fileModel.url_file)
              .into(((ItemMediaUserHolder) holder).imageContent);
          }
        }

        if (MediaChatActivity.MESSAGE_TYPE_VIDEO.equalsIgnoreCase(consersation.getListMessageData().get(position).fileModel.type)) {
          if (((ItemMediaUserHolder) holder).videoContent != null) {


            ((ItemMediaUserHolder) holder).videoContent.setVisibility(View.VISIBLE);

          }
        }

        if (MediaChatActivity.MESSAGE_TYPE_AUDIO.equalsIgnoreCase(consersation.getListMessageData().get(position).fileModel.type)) {
          if (((ItemMediaUserHolder) holder).audioContent != null) {
            ((ItemMediaUserHolder) holder).audioContent.setVisibility(View.VISIBLE);
            if (((ItemMediaUserHolder) holder).totalTime != null) {
              ((ItemMediaUserHolder) holder).totalTime.setText("test");
            }
            if (((ItemMediaUserHolder) holder).dateTime != null) {
              ((ItemMediaUserHolder) holder).dateTime.setText(String.valueOf(consersation.getListMessageData().get(position).timestamp));
            }

            //TODO AUDIO PLAYER
          }
        }
//        ((ItemMediaUserHolder) holder).imageContent.setImageURI(Uri.parse(consersation.getListMessageData().get(position).fileModel.url_file));
      } else {
        if (((ItemMediaUserHolder) holder).imageContent != null)
          ((ItemMediaUserHolder) holder).imageContent.setVisibility(View.INVISIBLE);

        if (consersation.getListMessageData().get(position).contact != null) {
          if (((ItemMediaUserHolder) holder).contactName != null)
            ((ItemMediaUserHolder) holder).contactName.setText(String.valueOf(consersation.getListMessageData().get(position).contact.name));

          if (((ItemMediaUserHolder) holder).contactPhone != null)
            ((ItemMediaUserHolder) holder).contactPhone.setText(String.valueOf(consersation.getListMessageData().get(position).contact.number));
        }
      }


    } else if (holder instanceof ItemMessageDate) {
      ((ItemMessageDate) holder).dateContent.setText(consersation.getListMessageData().get(position).date);

    }
  }

  @Override
  public int getItemViewType(int position) {

    if (consersation.getListMessageData().get(position).text == null && consersation.getListMessageData().get(position).fileModel != null) {
      if (MediaChatActivity.MESSAGE_TYPE_IMAGE.equals(consersation.getListMessageData().get(position).fileModel.type)) {
        return consersation.getListMessageData().get(position).idSender.equals(StaticConfig.UID)
          ? MediaChatActivity.VIEW_TYPE_USER_MESSAGE_IMAGE : MediaChatActivity.VIEW_TYPE_FRIEND_MESSAGE_IMAGE;
      }

      if (MediaChatActivity.MESSAGE_TYPE_VIDEO.equals(consersation.getListMessageData().get(position).fileModel.type)) {
        return consersation.getListMessageData().get(position).idSender.equals(StaticConfig.UID)
          ? MediaChatActivity.VIEW_TYPE_USER_MESSAGE_VIDEO : MediaChatActivity.VIEW_TYPE_FRIEND_MESSAGE_VIDEO;
      }

      if (MediaChatActivity.MESSAGE_TYPE_AUDIO.equals(consersation.getListMessageData().get(position).fileModel.type)) {
        return consersation.getListMessageData().get(position).idSender.equals(StaticConfig.UID)
          ? MediaChatActivity.VIEW_TYPE_USER_MESSAGE_AUDIO : MediaChatActivity.VIEW_TYPE_FRIEND_MESSAGE_AUDIO;
      }
    }

    return MediaChatActivity.VIEW_TYPE_MESSAGE_DATE;
  }

  @Override
  public int getItemCount() {
    return consersation.getListMessageData().size();
  }

}

class ItemMediaUserHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnTouchListener {
  public TextView txtContent;
  public TextView contactName;
  public TextView contactPhone;
  public ImageView imageContent;
  public ImageView videoContent;
  public ImageView play_audio;
  public ImageView pause_audio;
  public LinearLayout audioContent;
  public CircleImageView avata;

  public TextView totalTime;
  public TextView dateTime;

  public SeekBar user_seekbar;

  private ClickListenerChatFirebase clickListenerChatFirebase;

  public ItemMediaUserHolder(View itemView, ClickListenerChatFirebase clickListenerChatFirebase) {
    super(itemView);
    txtContent = (TextView) itemView.findViewById(R.id.textContentUser);
    contactName = (TextView) itemView.findViewById(R.id.userContactName);
    contactPhone = (TextView) itemView.findViewById(R.id.userContactPhone);

    imageContent = (ImageView) itemView.findViewById(R.id.imageContentUser);
    videoContent = (ImageView) itemView.findViewById(R.id.videoContentUser);
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

    if (imageContent != null) {
      imageContent.setOnClickListener(this);
    }

    if (videoContent != null) {
      videoContent.setOnClickListener(this);
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

      case R.id.imageContentUser:
        try {
          clickListenerChatFirebase.clickImageChat(v, position);
        } catch (Exception e) {
          e.printStackTrace();
        }
        break;

      case R.id.videoContentUser:
        try {
          clickListenerChatFirebase.clickVideoChat(v, position);
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

class ItemMediaFriendHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnTouchListener {
  public TextView txtContent;
  public TextView contactName;
  public TextView contactPhone;
  public CircleImageView avata;
  public ImageView imageContent;
  public ImageView videoContent;
  public LinearLayout audioContent;
  public ImageView play_audio;
  public ImageView pause_audio;

  public TextView totalTime;
  public TextView dateTime;

  public SeekBar friend_seekbar;

  private ClickListenerChatFirebase clickListenerChatFirebase;

  public ItemMediaFriendHolder(View itemView, ClickListenerChatFirebase clickListenerChatFirebase) {
    super(itemView);
    txtContent = (TextView) itemView.findViewById(R.id.textContentFriend);
    contactName = (TextView) itemView.findViewById(R.id.friendContactName);
    contactPhone = (TextView) itemView.findViewById(R.id.friendContactPhone);
    txtContent = (TextView) itemView.findViewById(R.id.textContentFriend);
    imageContent = (ImageView) itemView.findViewById(R.id.imageContentFriend);
    videoContent = (ImageView) itemView.findViewById(R.id.videoContentFriend);
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

    if (imageContent != null) {
      imageContent.setOnClickListener(this);
    }

    if (videoContent != null) {
      videoContent.setOnClickListener(this);
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

      case R.id.imageContentFriend:
        try {
          clickListenerChatFirebase.clickImageChat(v, position);
        } catch (Exception e) {
          e.printStackTrace();
        }
        break;


      case R.id.videoContentFriend:
        try {
          clickListenerChatFirebase.clickVideoChat(v, position);
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


class ItemMessageDate extends RecyclerView.ViewHolder {
  public TextView dateContent;
  private RecyclerView itemRecyclerView;

  public ItemMessageDate(View itemView) {
    super(itemView);
    dateContent = (TextView) itemView.findViewById(R.id.dateContent);

  }
}
