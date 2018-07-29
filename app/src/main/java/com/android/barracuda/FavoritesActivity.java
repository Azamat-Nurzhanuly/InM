package com.android.barracuda;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
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
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.barracuda.data.SharedPreferenceHelper;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.inter.ClickListenerChatFirebase;
import com.android.barracuda.model.FileModel;
import com.android.barracuda.model.Message;
import com.android.barracuda.ui.ChatActivity;
import com.android.barracuda.ui.ImageViewer;
import com.android.barracuda.ui.VideoViewer;
import com.bumptech.glide.Glide;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;

import static com.android.barracuda.R.color.colorMarked;
import static com.android.barracuda.ui.ChatActivity.MESSAGE_TYPE_AUDIO;
import static com.android.barracuda.ui.ChatActivity.MESSAGE_TYPE_IMAGE;
import static com.android.barracuda.ui.ChatActivity.MESSAGE_TYPE_VIDEO;
import static com.android.barracuda.ui.ChatActivity.bitmapAvataFriend;

public class FavoritesActivity extends MainActivity implements ClickListenerChatFirebase {

  private static final String TAG = FavoritesActivity.class.getName();
  private RecyclerView recyclerView;
  private List<Message> totalFavoriteMessages = new ArrayList<>();
  private ListFavoriteMessageAdapter adapter;

  private LinearLayoutManager linearLayoutManager;

  public Bitmap bitmapAvataUser;
  private Context context;

  //audio playing
  private MediaPlayer player;
  private String SAVED_URL_PLAY;
  private ImageView SAVED_PLAY_AUDIO;
  private ImageView SAVED_PAUSE_AUDIO;
  public Map<String, Integer> messageMap;
  public Map<Integer, Message> favoriteToDelMessages;
  public Map<String, View> messageViews = new HashMap<>();
  public Map<String, String> friendViews = new HashMap<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_favorites);

    getSupportActionBar().setTitle("Избранные сообщения");

    if (ChatActivity.bitmapAvataFriend == null) {
      ChatActivity.bitmapAvataFriend = new HashMap<>();
    }

    String base64AvataUser = SharedPreferenceHelper.getInstance(this).getUserInfo().avata;
    if (!base64AvataUser.equals(StaticConfig.STR_DEFAULT_BASE64)) {
      byte[] decodedString = Base64.decode(base64AvataUser, Base64.DEFAULT);
      bitmapAvataUser = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
    } else {
      bitmapAvataUser = null;
    }

    messageMap = new HashMap<>();
    favoriteToDelMessages = new HashMap<>();
    totalFavoriteMessages = new ArrayList<>();
    context = this;

    linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
    recyclerView = (RecyclerView) findViewById(R.id.recyclerFavorites);
    recyclerView.setLayoutManager(linearLayoutManager);
    adapter = new ListFavoriteMessageAdapter(this, totalFavoriteMessages, bitmapAvataFriend, bitmapAvataUser, this, this);

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
          adapter.notifyDataSetChanged();
          linearLayoutManager.scrollToPosition(totalFavoriteMessages.size() - 1);
        }
      }

      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String s) {}

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {}

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String s) {}

      @Override
      public void onCancelled(DatabaseError databaseError) {}
    });

    recyclerView.setAdapter(adapter);
  }

  @Override
  public void clickImageChat(View view, int position) {
    if (totalFavoriteMessages.get(position).fileModel != null &&
      MESSAGE_TYPE_IMAGE.equalsIgnoreCase(
        totalFavoriteMessages
          .get(position).fileModel.type)) {
      String url = totalFavoriteMessages.get(position).fileModel.url_file;

      Intent intent = new Intent(context, ImageViewer.class);
      intent.putExtra(StaticConfig.IMAGE_URL, url);
      startActivity(intent);
    }
  }

  @Override
  public void clickVideoChat(View view, int position) {
    if (totalFavoriteMessages.get(position).fileModel != null &&
      MESSAGE_TYPE_VIDEO.equalsIgnoreCase(
        totalFavoriteMessages
          .get(position).fileModel.type)) {


      String url = totalFavoriteMessages.get(position).fileModel.url_file;

      Intent intent = new Intent(context, VideoViewer.class);
      intent.putExtra(StaticConfig.VIDEO_URL, url);
      startActivity(intent);
    }
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
    if (totalFavoriteMessages.get(position).fileModel != null &&
      MESSAGE_TYPE_AUDIO.equalsIgnoreCase(totalFavoriteMessages.get(position).fileModel.type)
      && SAVED_URL_PLAY == null && player == null) {
      SAVED_URL_PLAY = totalFavoriteMessages.get(position).fileModel.url_file;
      player = new MediaPlayer();
      try {
        player.setDataSource(totalFavoriteMessages.get(position).fileModel.url_file);
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
      SAVED_URL_PLAY.equalsIgnoreCase(totalFavoriteMessages.get(position).fileModel.url_file) &&
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
    if (totalFavoriteMessages.get(position).fileModel != null &&
      MESSAGE_TYPE_AUDIO.equalsIgnoreCase(totalFavoriteMessages.get(position).fileModel.type) &&
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

  MenuItem removeFavorites;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_favorites, menu);
    removeFavorites = menu.getItem(0);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    switch (item.getItemId()) {
      case android.R.id.home: {
        Intent result = new Intent();
        setResult(RESULT_OK, result);
        this.finish();
        break;
      }
      case R.id.removeFavorites: {

        for (Iterator<Map.Entry<Integer, Message>> it = favoriteToDelMessages.entrySet().iterator(); it.hasNext(); ) {
          final Map.Entry<Integer, Message> entry = it.next();

          if (totalFavoriteMessages.contains(entry.getValue())) {

            try {
              totalFavoriteMessages.remove(entry.getValue());
              recyclerView.removeViewAt(entry.getKey());
              adapter.notifyItemRemoved(entry.getKey());
              adapter.notifyItemRangeChanged(entry.getKey(), totalFavoriteMessages.size());
            } catch (Exception e) {}
          }

          FirebaseDatabase.getInstance().getReference().child("favorites").child(StaticConfig.UID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

              for (DataSnapshot snapshot : dataSnapshot.getChildren()) {

                if (snapshot.getValue() != null) {
                  HashMap mapMessage = (HashMap) snapshot.getValue();

                  Message newMessage = new Message();
                  newMessage.idSender = (String) mapMessage.get("idSender");
                  newMessage.idReceiver = (String) mapMessage.get("idReceiver");
                  newMessage.timestamp = (long) mapMessage.get("timestamp");

                  if (!Objects.equals(entry.getValue().idSender, newMessage.idSender)) {
                    continue;
                  }

                  if (!Objects.equals(entry.getValue().idReceiver, newMessage.idReceiver)) {
                    continue;
                  }

                  if (!Objects.equals(entry.getValue().timestamp, newMessage.timestamp)) {
                    continue;
                  }

                  snapshot.getRef().removeValue();
                }
              }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
          });
        }

        favoriteToDelMessages.clear();
        for (Iterator<Map.Entry<String, View>> it = messageViews.entrySet().iterator(); it.hasNext(); ) {
          Map.Entry<String, View> entry = it.next();
          entry.getValue().setBackgroundColor(Color.argb(0, 255, 255, 255));
        }

        if (removeFavorites != null) {
          removeFavorites.setEnabled(false);
          removeFavorites.setVisible(false);
        }

        break;
      }
    }

    return true;
  }

  @SuppressLint("ResourceAsColor")
  public void onMessageMark(View view) {

    Integer position = messageMap.get(view.toString().substring(30, 38));

    if (position == null) return;

    Message message = totalFavoriteMessages.get(position);

    if (message == null) return;

    switch (view.getBackground().getAlpha()) {
      case 0: {
        view.setBackgroundColor(colorMarked);

        favoriteToDelMessages.put(position, message);
        break;
      }
      default: {
        view.setBackgroundColor(Color.argb(0, 255, 255, 255));

        for (Iterator<Map.Entry<Integer, Message>> it = favoriteToDelMessages.entrySet().iterator(); it.hasNext(); ) {
          Map.Entry<Integer, Message> entry = it.next();
          if (Objects.equals(entry.getKey(), position)) {
            it.remove();
          }
        }

        break;
      }
    }

    if (favoriteToDelMessages.size() > 0) {

      if (removeFavorites != null) {
        removeFavorites.setEnabled(true);
        removeFavorites.setVisible(true);
      }
    } else {

      if (removeFavorites != null) {
        removeFavorites.setEnabled(false);
        removeFavorites.setVisible(false);
      }
    }
  }

  public void onFriendImgClick(View view) {

    Intent profIntent = new Intent(this, FriendProfileActivity.class);
    profIntent.putExtra("friend_id", this.friendViews.get(view.toString().substring(30, 38)));
    startActivity(profIntent);
  }
}

class ListFavoriteMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private Context context;
  public List<Message> totalFavoriteMessages;
  private HashMap<String, Bitmap> bitmapAvata;
  private HashMap<String, DatabaseReference> bitmapAvataDB;
  private Bitmap bitmapAvataUser;
  private ClickListenerChatFirebase clickListenerChatFirebase;
  FavoritesActivity activity;

  public ListFavoriteMessageAdapter(Context context, List<Message> totalFavoriteMessages, HashMap<String, Bitmap> bitmapAvata, Bitmap bitmapAvataUser, ClickListenerChatFirebase clickListenerChatFirebase, FavoritesActivity activity) {
    this.context = context;
    this.totalFavoriteMessages = totalFavoriteMessages;
    this.bitmapAvata = bitmapAvata;
    this.bitmapAvataUser = bitmapAvataUser;
    bitmapAvataDB = new HashMap<>();
    this.clickListenerChatFirebase = clickListenerChatFirebase;
    this.activity = activity;
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
    } else if (viewType == ChatActivity.VIEW_TYPE_USER_MESSAGE_VIDEO) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_user_video, parent, false);
      return new ItemMessageUserHolder(view, clickListenerChatFirebase);
    } else if (viewType == ChatActivity.VIEW_TYPE_FRIEND_MESSAGE_VIDEO) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_friend_video, parent, false);
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

    this.activity.messageMap.put(holder.itemView.toString().substring(30, 38), position);
    this.activity.messageViews.put(holder.itemView.toString().substring(30, 38), holder.itemView);

    if (holder instanceof ItemMessageFriendHolder) {
      this.activity.friendViews.put(((ItemMessageFriendHolder) holder).avata.toString().substring(30, 38), totalFavoriteMessages.get(position).idSender);
    }

    if (holder instanceof ItemMessageFriendHolder) {
      if (totalFavoriteMessages.get(position).text != null) {
        if (((ItemMessageFriendHolder) holder).txtContent != null) {
          ((ItemMessageFriendHolder) holder).txtContent.setVisibility(View.VISIBLE);
          ((ItemMessageFriendHolder) holder).txtContent.setText(totalFavoriteMessages.get(position).text);
        }
      } else {
        if (((ItemMessageFriendHolder) holder).txtContent != null)
          ((ItemMessageFriendHolder) holder).txtContent.setVisibility(View.INVISIBLE);
      }
      if (totalFavoriteMessages.get(position).fileModel != null) {

        if (MESSAGE_TYPE_IMAGE.equalsIgnoreCase(totalFavoriteMessages.get(position).fileModel.type)) {
          if (((ItemMessageFriendHolder) holder).imageContent != null) {
            ((ItemMessageFriendHolder) holder).imageContent.setVisibility(View.VISIBLE);

            Glide.with(((ItemMessageFriendHolder) holder).imageContent.getContext())
              .load(totalFavoriteMessages.get(position).fileModel.url_file)
              .into(((ItemMessageFriendHolder) holder).imageContent);
          }
        }

        if (MESSAGE_TYPE_VIDEO.equalsIgnoreCase(totalFavoriteMessages.get(position).fileModel.type)) {
          if (((ItemMessageFriendHolder) holder).videoContent != null) {


            ((ItemMessageFriendHolder) holder).videoContent.setVisibility(View.VISIBLE);

          }
        }

        if (MESSAGE_TYPE_AUDIO.equalsIgnoreCase(totalFavoriteMessages.get(position).fileModel.type)) {
          if (((ItemMessageFriendHolder) holder).audioContent != null) {
            ((ItemMessageFriendHolder) holder).audioContent.setVisibility(View.VISIBLE);
          }
        }

//        ((ItemMessageUserHolder) holder).imageContent.setImageURI(Uri.parse(totalFavoriteMessages.get(position).fileModel.url_file));
      } else {
        if (((ItemMessageFriendHolder) holder).imageContent != null) {
          ((ItemMessageFriendHolder) holder).imageContent.setVisibility(View.INVISIBLE);
        }

        if (((ItemMessageFriendHolder) holder).audioContent != null) {
          ((ItemMessageFriendHolder) holder).audioContent.setVisibility(View.INVISIBLE);
        }
      }

      Bitmap currentAvata = null;

      if (bitmapAvata != null) {
        currentAvata = bitmapAvata.get(totalFavoriteMessages.get(position).idSender);
      }

      if (currentAvata != null) {
        ((ItemMessageFriendHolder) holder).avata.setImageBitmap(currentAvata);
      } else {
        final String id = totalFavoriteMessages.get(position).idSender;
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


      if (totalFavoriteMessages.get(position).text != null) {
        if (((ItemMessageUserHolder) holder).txtContent != null) {
          ((ItemMessageUserHolder) holder).txtContent.setVisibility(View.VISIBLE);
          ((ItemMessageUserHolder) holder).txtContent.setText(totalFavoriteMessages.get(position).text);
        }
      } else {
        if (((ItemMessageUserHolder) holder).txtContent != null)
          ((ItemMessageUserHolder) holder).txtContent.setVisibility(View.INVISIBLE);
      }
      if (totalFavoriteMessages.get(position).fileModel != null) {

        if (MESSAGE_TYPE_IMAGE.equalsIgnoreCase(totalFavoriteMessages.get(position).fileModel.type)) {
          if (((ItemMessageUserHolder) holder).imageContent != null) {


            ((ItemMessageUserHolder) holder).imageContent.setVisibility(View.VISIBLE);

            Glide.with(((ItemMessageUserHolder) holder).imageContent.getContext())
              .load(totalFavoriteMessages.get(position).fileModel.url_file)
              .into(((ItemMessageUserHolder) holder).imageContent);
          }
        }

        if (MESSAGE_TYPE_VIDEO.equalsIgnoreCase(totalFavoriteMessages.get(position).fileModel.type)) {
          if (((ItemMessageUserHolder) holder).videoContent != null) {


            ((ItemMessageUserHolder) holder).videoContent.setVisibility(View.VISIBLE);

          }
        }

        if (MESSAGE_TYPE_AUDIO.equalsIgnoreCase(totalFavoriteMessages.get(position).fileModel.type)) {
          if (((ItemMessageUserHolder) holder).audioContent != null) {
            ((ItemMessageUserHolder) holder).audioContent.setVisibility(View.VISIBLE);
            if (((ItemMessageUserHolder) holder).totalTime != null) {
              ((ItemMessageUserHolder) holder).totalTime.setText("test");
            }
            if (((ItemMessageUserHolder) holder).dateTime != null) {
              ((ItemMessageUserHolder) holder).dateTime.setText(String.valueOf(totalFavoriteMessages.get(position).timestamp));
            }
          }
        }


//        ((ItemMessageUserHolder) holder).imageContent.setImageURI(Uri.parse(totalFavoriteMessages.get(position).fileModel.url_file));
      } else {
        if (((ItemMessageUserHolder) holder).imageContent != null)
          ((ItemMessageUserHolder) holder).imageContent.setVisibility(View.INVISIBLE);
      }
    }
  }

  @Override
  public int getItemViewType(int position) {
    if (totalFavoriteMessages.get(position).text == null && totalFavoriteMessages.get(position).fileModel != null) {

      if (MESSAGE_TYPE_IMAGE.equals(totalFavoriteMessages.get(position).fileModel.type)) {
        return totalFavoriteMessages.get(position).idSender.equals(StaticConfig.UID)
          ? ChatActivity.VIEW_TYPE_USER_MESSAGE_IMAGE : ChatActivity.VIEW_TYPE_FRIEND_MESSAGE_IMAGE;
      }

      if (MESSAGE_TYPE_VIDEO.equals(totalFavoriteMessages.get(position).fileModel.type)) {
        return totalFavoriteMessages.get(position).idSender.equals(StaticConfig.UID)
          ? ChatActivity.VIEW_TYPE_USER_MESSAGE_VIDEO : ChatActivity.VIEW_TYPE_FRIEND_MESSAGE_VIDEO;
      }

      if (MESSAGE_TYPE_AUDIO.equals(totalFavoriteMessages.get(position).fileModel.type)) {
        return totalFavoriteMessages.get(position).idSender.equals(StaticConfig.UID)
          ? ChatActivity.VIEW_TYPE_USER_MESSAGE_AUDIO : ChatActivity.VIEW_TYPE_FRIEND_MESSAGE_AUDIO;
      }
    }

    return totalFavoriteMessages.get(position).idSender.equals(StaticConfig.UID)
      ? ChatActivity.VIEW_TYPE_USER_MESSAGE_TEXT : ChatActivity.VIEW_TYPE_FRIEND_MESSAGE_TEXT;

  }

  @Override
  public int getItemCount() {
    return totalFavoriteMessages.size();
  }

}

class ItemMessageUserHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnTouchListener {
  public TextView txtContent;
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

  public ItemMessageUserHolder(View itemView, ClickListenerChatFirebase clickListenerChatFirebase) {
    super(itemView);
    txtContent = (TextView) itemView.findViewById(R.id.textContentUser);
    imageContent = (ImageView) itemView.findViewById(R.id.imageContentUser);
    videoContent = (ImageView) itemView.findViewById(R.id.videoContentUser);
    audioContent = (LinearLayout) itemView.findViewById(R.id.audioUserView);
    play_audio = (ImageView) itemView.findViewById(R.id.play_audio);
    pause_audio = (ImageView) itemView.findViewById(R.id.pause_audio);
    user_seekbar = (SeekBar) itemView.findViewById(R.id.user_seekbar);
    avata = (CircleImageView) itemView.findViewById(R.id.imageView2);

    totalTime = (TextView) itemView.findViewById(R.id.totalTimeAudio);
    dateTime = (TextView) itemView.findViewById(R.id.dateTimeAudio);

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

class ItemMessageFriendHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnTouchListener {
  public TextView txtContent;
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

  public ItemMessageFriendHolder(View itemView, ClickListenerChatFirebase clickListenerChatFirebase) {
    super(itemView);
    txtContent = (TextView) itemView.findViewById(R.id.textContentFriend);
    imageContent = (ImageView) itemView.findViewById(R.id.imageContentFriend);
    videoContent = (ImageView) itemView.findViewById(R.id.videoContentFriend);
    audioContent = (LinearLayout) itemView.findViewById(R.id.audioFriendView);
    play_audio = (ImageView) itemView.findViewById(R.id.play_audio);
    pause_audio = (ImageView) itemView.findViewById(R.id.pause_audio);
    friend_seekbar = (SeekBar) itemView.findViewById(R.id.friend_seekbar);
    avata = (CircleImageView) itemView.findViewById(R.id.imageView3);

    totalTime = (TextView) itemView.findViewById(R.id.totalTimeAudio);
    dateTime = (TextView) itemView.findViewById(R.id.dateTimeAudio);

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


