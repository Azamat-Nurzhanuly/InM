package com.android.barracuda;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.Friend;
import com.android.barracuda.model.ListFriend;
import com.android.barracuda.service.ServiceUtils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.yarolegovich.lovelydialog.LovelyInfoDialog;
import com.yarolegovich.lovelydialog.LovelyProgressDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;

import static com.android.barracuda.ui.ChatActivity.getBackground;

public class BlacklistActivity extends MainActivity {

  private Context context;
  public ListFriend dataBlackListFriend;
  public BlackListFriendsAdapter adapter;
  private RecyclerView recyclerBlackListFrends;
  private CountDownTimer detectFriendOnline;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_blacklist);

    context = this;

    int background = getBackground(this);
    View wallpaper = findViewById(R.id.wallpaper);
    wallpaper.setBackgroundResource(background);

    getSupportActionBar().setTitle("Черный список");

    detectFriendOnline = new CountDownTimer(System.currentTimeMillis(), StaticConfig.TIME_TO_REFRESH) {
      @Override
      public void onTick(long l) {
        ServiceUtils.updateFriendStatus(context, dataBlackListFriend);
        ServiceUtils.updateUserStatus(context);
      }

      @Override
      public void onFinish() {

      }
    };

    detectFriendOnline.start();

    if (dataBlackListFriend == null) {

      dataBlackListFriend = new ListFriend();

      FirebaseDatabase.getInstance().getReference()
        .child("blacklist")
        .child(StaticConfig.UID)
        .addListenerForSingleValueEvent(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot dataSnapshot) {
            if (dataSnapshot.getValue() != null) {

              for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                final String friendId = (String) snapshot.getValue();

                FirebaseDatabase.getInstance().getReference()
                  .child("user")
                  .child(friendId).addListenerForSingleValueEvent(new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() != null) {

                      HashMap mapFriend = (HashMap) dataSnapshot.getValue();

                      Friend friend = new Friend();
                      friend.phoneNumber = (String) mapFriend.get("phoneNumber");
                      friend.name = (String) mapFriend.get("name");
                      friend.avata = (String) mapFriend.get("avata");
                      friend.id = (String) mapFriend.get("id");


                      HashMap mapStatus = (HashMap) mapFriend.get("status");
                      friend.status.text = (String) mapStatus.get("text");
                      friend.status.isOnline = (Boolean) mapStatus.get("isOnline");
                      friend.status.timestamp = (Long) mapStatus.get("timestamp");

                      dataBlackListFriend.getListFriend().add(friend);
                      adapter.notifyDataSetChanged();
                    }
                  }

                  @Override
                  public void onCancelled(DatabaseError databaseError) {

                  }
                });
              }
            }
          }

          @Override
          public void onCancelled(DatabaseError databaseError) {

          }
        });
    }

    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
    recyclerBlackListFrends = (RecyclerView) findViewById(R.id.recycleBlackListFriend);
    recyclerBlackListFrends.setLayoutManager(linearLayoutManager);
    adapter = new BlackListFriendsAdapter(context, dataBlackListFriend);
    recyclerBlackListFrends.setAdapter(adapter);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    return false;
  }
}

class BlackListFriendsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  public ListFriend blackListFriend;
  private Context context;
  public static Map<String, DatabaseReference> mapQueryOnline;
  public static Map<String, ChildEventListener> mapChildListenerOnline;
  LovelyProgressDialog dialogWaitDeleting;
  public BlacklistActivity activity;

  public BlackListFriendsAdapter(Context context, ListFriend blackListFriend) {
    this.blackListFriend = blackListFriend;
    this.context = context;
    mapChildListenerOnline = new HashMap<>();
    mapQueryOnline = new HashMap<>();
    dialogWaitDeleting = new LovelyProgressDialog(context);
    activity = (BlacklistActivity) context;
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(context).inflate(R.layout.rc_item_blacklist_friend, parent, false);
    return new ItemBlacklistFriendViewHolder(context, view);
  }

  @Override
  public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
    final String name = blackListFriend.getListFriend().get(position).name;
    final String status = blackListFriend.getListFriend().get(position).status.text;
    final String id = blackListFriend.getListFriend().get(position).id;
    final String avata = blackListFriend.getListFriend().get(position).avata;
    ((ItemBlacklistFriendViewHolder) holder).txtName.setText(name);
    ((ItemBlacklistFriendViewHolder) holder).txtStatus.setText(status);

    ((View) ((ItemBlacklistFriendViewHolder) holder).txtName.getParent().getParent().getParent())
      .setOnLongClickListener(new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
          String friendName = (String) ((ItemBlacklistFriendViewHolder) holder).txtName.getText();

          new AlertDialog.Builder(context)
            .setTitle("Удалить из черного списка")
            .setMessage("Вы точно хотите удалить " + friendName + " из черного списка?")
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                final String idFriendRemoval = blackListFriend.getListFriend().get(position).id;
                dialogWaitDeleting.setTitle("Удаление...")
                  .setCancelable(false)
                  .setTopColorRes(R.color.colorAccent)
                  .show();
                removeFromBlackList(idFriendRemoval, position);
              }
            })
            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
              }
            }).show();

          return true;
        }
      });

    if (blackListFriend.getListFriend().get(position).status.text != null && blackListFriend.getListFriend().get(position).status.text.length() > 0) {
      ((ItemBlacklistFriendViewHolder) holder).txtStatus.setVisibility(View.VISIBLE);
      ((ItemBlacklistFriendViewHolder) holder).txtStatus.setText(blackListFriend.getListFriend().get(position).status.text);
      ((ItemBlacklistFriendViewHolder) holder).txtStatus.setTypeface(Typeface.DEFAULT);
      ((ItemBlacklistFriendViewHolder) holder).txtName.setTypeface(Typeface.DEFAULT);
    } else {
      ((ItemBlacklistFriendViewHolder) holder).txtStatus.setVisibility(View.GONE);
    }

    if (StaticConfig.STR_DEFAULT_BASE64.equals(blackListFriend.getListFriend().get(position).avata)) {
      ((ItemBlacklistFriendViewHolder) holder).avata.setImageResource(R.drawable.default_avata);
    } else {

      if (blackListFriend.getListFriend().get(position).avata != null) {
        byte[] decodedString = Base64.decode(blackListFriend.getListFriend().get(position).avata, Base64.DEFAULT);
        Bitmap src = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        ((ItemBlacklistFriendViewHolder) holder).avata.setImageBitmap(src);
      }
    }

    if (mapQueryOnline.get(id) == null && mapChildListenerOnline.get(id) == null) {
      mapQueryOnline.put(id, FirebaseDatabase.getInstance().getReference().child("user/" + id + "/status"));
      mapChildListenerOnline.put(id, new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
          if (dataSnapshot.getValue() != null && dataSnapshot.getKey().equals("isOnline")) {
            Log.d("BlacklistFriends add " + id, (boolean) dataSnapshot.getValue() + "");
            blackListFriend.getListFriend().get(position).status.isOnline = (boolean) dataSnapshot.getValue();
            notifyDataSetChanged();
          }
        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String s) {
          if (dataSnapshot.getValue() != null && dataSnapshot.getKey().equals("isOnline")) {
            Log.d("BlacklistFrnds change " + id, (boolean) dataSnapshot.getValue() + "");
            blackListFriend.getListFriend().get(position).status.isOnline = (boolean) dataSnapshot.getValue();
            notifyDataSetChanged();
          }
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
      mapQueryOnline.get(id).addChildEventListener(mapChildListenerOnline.get(id));
    }

    if (blackListFriend.getListFriend().get(position).status.isOnline) {
      ((ItemBlacklistFriendViewHolder) holder).avata.setBorderWidth(10);
    } else {
      ((ItemBlacklistFriendViewHolder) holder).avata.setBorderWidth(0);
    }
  }

  private void removeFromBlackList(final String idFriendRemoval, final int position) {
    dialogWaitDeleting.setTitle("Удаление...")
      .setCancelable(false)
      .setTopColorRes(R.color.colorAccent)
      .show();

    FirebaseDatabase.getInstance().getReference().child("blacklist").child(StaticConfig.UID).orderByValue().equalTo(idFriendRemoval).addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        if (dataSnapshot.getValue() != null) {

          final String idRemoval = ((HashMap) dataSnapshot.getValue()).keySet().iterator().next().toString();
          FirebaseDatabase.getInstance().getReference().child("blacklist")
            .child(StaticConfig.UID).child(idRemoval).removeValue()
            .addOnCompleteListener(new OnCompleteListener<Void>() {
              @Override
              public void onComplete(@NonNull Task<Void> task) {
                dialogWaitDeleting.dismiss();

                new LovelyInfoDialog(context)
                  .setTopColorRes(R.color.colorAccent)
                  .setTitle("Успешно")
                  .setMessage("Удален из черного списка")
                  .show();

                for(int i = 0; i < activity.dataBlackListFriend.getListFriend().size(); i++) {

                  Friend friend = activity.dataBlackListFriend.getListFriend().get(i);

                  if(Objects.equals(idFriendRemoval, friend.id)) {

                    activity.dataBlackListFriend.getListFriend().remove(i);
                    break;
                  }
                }

                activity.adapter.notifyItemChanged(position);
              }
            })
            .addOnFailureListener(new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                dialogWaitDeleting.dismiss();
                new LovelyInfoDialog(context)
                  .setTopColorRes(R.color.colorAccent)
                  .setTitle("Ошибка")
                  .setMessage("Не удалось удалить друга из черного списка")
                  .show();
              }
            });
        }
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {

      }
    });
  }

  @Override
  public int getItemCount() {
    return blackListFriend.getListFriend() != null ? blackListFriend.getListFriend().size() : 0;
  }
}

class ItemBlacklistFriendViewHolder extends RecyclerView.ViewHolder {
  public CircleImageView avata;
  public TextView txtName, txtStatus;
  private Context context;

  ItemBlacklistFriendViewHolder(Context context, View itemView) {
    super(itemView);
    avata = (CircleImageView) itemView.findViewById(R.id.icon_avata);
    txtName = (TextView) itemView.findViewById(R.id.txtName);
    txtStatus = (TextView) itemView.findViewById(R.id.txtStatus);
    this.context = context;
  }
}
