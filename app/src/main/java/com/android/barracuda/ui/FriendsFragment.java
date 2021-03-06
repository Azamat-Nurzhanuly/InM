package com.android.barracuda.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.barracuda.MainActivity;
import com.android.barracuda.R;
import com.android.barracuda.data.FriendDB;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.FileModel;
import com.android.barracuda.model.Friend;
import com.android.barracuda.model.ListFriend;
import com.android.barracuda.service.ServiceUtils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.yarolegovich.lovelydialog.LovelyInfoDialog;
import com.yarolegovich.lovelydialog.LovelyProgressDialog;
import com.yarolegovich.lovelydialog.LovelyTextInputDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.hdodenhof.circleimageview.CircleImageView;

import static com.android.barracuda.MainActivity.friendsMap;

public class FriendsFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

  private RecyclerView recyclerListFrends;
  private ListFriendsAdapter adapter;
  public FragFriendClickFloatButton onClickFloatButton;
  private ListFriend dataListFriend = null;
  private ArrayList<String> listFriendID = null;
  private LovelyProgressDialog dialogFindAllFriend;
  private SwipeRefreshLayout mSwipeRefreshLayout;
  private CountDownTimer detectFriendOnline;
  public Map<String, Integer> listViewPosFriend = new HashMap<>();
  public Map<Integer, String> listViewNameFriend = new HashMap<>();
  public static int ACTION_START_CHAT = 1;

  public static final String ACTION_DELETE_FRIEND = "DELETE_FRIEND";

  private BroadcastReceiver deleteFriendReceiver;
  private LovelyProgressDialog dialogWaitDeleting;

  public FriendsFragment() {
    onClickFloatButton = new FragFriendClickFloatButton();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    dialogWaitDeleting = new LovelyProgressDialog(getContext());
  }

  @Override
  public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    detectFriendOnline = new CountDownTimer(System.currentTimeMillis(), StaticConfig.TIME_TO_REFRESH) {
      @Override
      public void onTick(long l) {
        ServiceUtils.updateFriendStatus(getContext(), dataListFriend);
        ServiceUtils.updateUserStatus(getContext());
      }

      @Override
      public void onFinish() {

      }
    };

    dataListFriend = new ListFriend();
    if (dataListFriend == null) {
      dataListFriend = FriendDB.getInstance(getContext()).getListFriend();
      if (dataListFriend.getListFriend().size() > 0) {
        listFriendID = new ArrayList<>();
        for (Friend friend : dataListFriend.getListFriend()) {
          listFriendID.add(friend.id);
        }
        detectFriendOnline.start();
      }
    }

    View layout = inflater.inflate(R.layout.fragment_people, container, false);
    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
    recyclerListFrends = (RecyclerView) layout.findViewById(R.id.recycleListFriend);
    recyclerListFrends.setLayoutManager(linearLayoutManager);
    mSwipeRefreshLayout = (SwipeRefreshLayout) layout.findViewById(R.id.swipeRefreshLayout);
    mSwipeRefreshLayout.setOnRefreshListener(this);
    adapter = new ListFriendsAdapter(getContext(), dataListFriend, this);
    recyclerListFrends.setAdapter(adapter);
    dialogFindAllFriend = new LovelyProgressDialog(getContext());
    if (listFriendID == null) {
      listFriendID = new ArrayList<>();
    }

//    dialogFindAllFriend.setCancelable(false)
//      .setIcon(R.drawable.ic_add_friend)
//      .setTitle("Получение списка друзей....")
//      .setTopColorRes(R.color.colorPrimary)
//      .show();
    getListFriendUId();

    deleteFriendReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String idDeleted = intent.getExtras().getString("idFriend");
        for (Friend friend : dataListFriend.getListFriend()) {
          if (idDeleted.equals(friend.id)) {
            ArrayList<Friend> friends = dataListFriend.getListFriend();
            friends.remove(friend);
            break;
          }
        }
        adapter.notifyDataSetChanged();
      }
    };

    IntentFilter intentFilter = new IntentFilter(ACTION_DELETE_FRIEND);
    getContext().registerReceiver(deleteFriendReceiver, intentFilter);

    return layout;
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();

    getContext().unregisterReceiver(deleteFriendReceiver);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (ACTION_START_CHAT == requestCode && data != null && ListFriendsAdapter.mapMark != null) {
      ListFriendsAdapter.mapMark.put(data.getStringExtra("idFriend"), false);
    }
  }

  @Override
  public void onRefresh() {
    listFriendID.clear();
    dataListFriend.getListFriend().clear();
    adapter.notifyDataSetChanged();
    FriendDB.getInstance(getContext()).dropDB();
    detectFriendOnline.cancel();
    getListFriendUId();
  }

  public class FragFriendClickFloatButton implements View.OnClickListener {
    Context context;
    LovelyProgressDialog dialogWait;

    public FragFriendClickFloatButton() {
    }

    public FragFriendClickFloatButton getInstance(Context context) {
      this.context = context;
      dialogWait = new LovelyProgressDialog(context);
      return this;
    }

    @Override
    public void onClick(final View view) {

      int themeColor = MainActivity.getThemeColor(getActivity());

      new LovelyTextInputDialog(view.getContext(), R.style.EditTextTintTheme)
        .setTopColorRes(themeColor)
        .setTitle("Добавление друга")
        .setMessage("Введите номер телефона друга")
        .setIcon(R.drawable.ic_add_friend)
        .setInputType(InputType.TYPE_CLASS_PHONE)
        .setInputFilter("Телефонный номер не найден", new LovelyTextInputDialog.TextFilter() {
          @Override
          public boolean check(String text) {
            Pattern VALID_EMAIL_ADDRESS_REGEX =
              Pattern.compile("\\d+", Pattern.CASE_INSENSITIVE);
            Matcher matcher = VALID_EMAIL_ADDRESS_REGEX.matcher(text);
            return matcher.find();
          }
        })
        .setConfirmButton(android.R.string.ok, new LovelyTextInputDialog.OnTextInputConfirmListener() {
          @Override
          public void onTextInputConfirmed(String text) {
            findIDPhoneNumber(text);
          }
        })
        .show();
    }

    private void findIDPhoneNumber(String phoneNumber) {

      int themeColor = MainActivity.getThemeColor(getActivity());

      dialogWait.setCancelable(false)
        .setIcon(R.drawable.ic_add_friend)
        .setTitle("Поиск друга....")
        .setTopColorRes(themeColor)
        .show();
      FirebaseDatabase.getInstance().getReference().child("user").orderByChild("phoneNumber").equalTo(phoneNumber).addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
          dialogWait.dismiss();
          if (dataSnapshot.getValue() == null) {
            //phoneNumber not found
            new LovelyInfoDialog(context)
              .setTopColorRes(R.color.colorAccent)
              .setIcon(R.drawable.ic_add_friend)
              .setTitle("Ошибка")
              .setMessage("Телефонный номер не найден")
              .show();
          } else {
            String id = ((HashMap) dataSnapshot.getValue()).keySet().iterator().next().toString();
            if (id.equals(StaticConfig.UID)) {
              new LovelyInfoDialog(context)
                .setTopColorRes(R.color.colorAccent)
                .setIcon(R.drawable.ic_add_friend)
                .setTitle("Ошибка")
                .setMessage("Телефонный номер неверен")
                .show();
            } else {
              HashMap userMap = (HashMap) ((HashMap) dataSnapshot.getValue()).get(id);
              Friend user = new Friend();
              user.name = (String) userMap.get("name");
              user.phoneNumber = (String) userMap.get("phoneNumber");
              user.avata = (String) userMap.get("avata");
              user.id = id;
              user.idRoom = id.compareTo(StaticConfig.UID) > 0 ? (StaticConfig.UID + id).hashCode() + "" : "" + (id + StaticConfig.UID).hashCode();
              checkBeforeAddFriend(id, user);
            }
          }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
      });
    }

    /**
     * Lay danh sach friend cua một UID
     */
    private void checkBeforeAddFriend(final String idFriend, Friend userInfo) {

      int themeColor = MainActivity.getThemeColor(getActivity());

      dialogWait.setCancelable(false)
        .setIcon(R.drawable.ic_add_friend)
        .setTitle("Добавление друга....")
        .setTopColorRes(themeColor)
        .show();

      //Check xem da ton tai id trong danh sach id chua
      if (listFriendID.contains(idFriend)) {
        dialogWait.dismiss();
        new LovelyInfoDialog(context)
          .setTopColorRes(themeColor)
          .setIcon(R.drawable.ic_add_friend)
          .setTitle("Друг")
          .setMessage("Пользователь " + userInfo.phoneNumber + " добавлен в друзья")
          .show();
      } else {
        addFriend(idFriend, true);
        listFriendID.add(idFriend);
        dataListFriend.getListFriend().add(userInfo);
        FriendDB.getInstance(getContext()).addFriend(userInfo);
        adapter.notifyDataSetChanged();
      }
    }

    /**
     * Add friend
     *
     * @param idFriend
     */
    private void addFriend(final String idFriend, boolean isIdFriend) {
      if (idFriend != null) {
        if (isIdFriend) {
          FirebaseDatabase.getInstance().getReference().child("friend/" + StaticConfig.UID).push().setValue(idFriend)
            .addOnCompleteListener(new OnCompleteListener<Void>() {
              @Override
              public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                  addFriend(idFriend, false);
                }
              }
            })
            .addOnFailureListener(new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                dialogWait.dismiss();
                new LovelyInfoDialog(context)
                  .setTopColorRes(R.color.colorAccent)
                  .setIcon(R.drawable.ic_add_friend)
                  .setTitle("Ошибка")
                  .setMessage("Не удалось добавить друга")
                  .show();
              }
            });
        } else {
          FirebaseDatabase.getInstance().getReference().child("friend/" + idFriend).push().setValue(StaticConfig.UID).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
              if (task.isSuccessful()) {
                addFriend(null, false);
              }
            }
          })
            .addOnFailureListener(new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                dialogWait.dismiss();
                new LovelyInfoDialog(context)
                  .setTopColorRes(R.color.colorAccent)
                  .setIcon(R.drawable.ic_add_friend)
                  .setTitle("Ошибка")
                  .setMessage("Не удалось добавить друга")
                  .show();
              }
            });
        }
      } else {
        dialogWait.dismiss();

        int themeColor = MainActivity.getThemeColor(getActivity());

        new LovelyInfoDialog(context)
          .setTopColorRes(themeColor)
          .setIcon(R.drawable.ic_add_friend)
          .setTitle("Успешно")
          .setMessage("Друг успешно добавлен")
          .show();
      }
    }


  }

  private void getListFriendUId() {
    System.out.println("GET_LIST_FRIEND_STARTED");
    FirebaseDatabase.getInstance().getReference().child("friend/" + StaticConfig.UID).addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {

        System.out.println("GET_LIST_FRIEND_CAME");

        if (dataSnapshot.getValue() != null) {
          HashMap mapRecord = (HashMap) dataSnapshot.getValue();
          Iterator listKey = mapRecord.keySet().iterator();
          while (listKey.hasNext()) {
            String key = listKey.next().toString();
            if (!listFriendID.contains(mapRecord.get(key).toString()))
              listFriendID.add(mapRecord.get(key).toString());
          }
          getAllFriendInfo(0);
        } else {
          dialogFindAllFriend.dismiss();
        }

        mSwipeRefreshLayout.setRefreshing(false);
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
        dialogFindAllFriend.dismiss();
      }
    });
  }

  private void getAllFriendInfo(final int index) {

    System.out.println("GET_ALL_FRIEND_STARTED");

    if (index == listFriendID.size()) {
      //save list friend
      adapter.notifyDataSetChanged();
      dialogFindAllFriend.dismiss();
      mSwipeRefreshLayout.setRefreshing(false);
      detectFriendOnline.start();
      System.out.println("GET_ALL_FRIEND_FINISHED");
    } else {

      System.out.println("GET_ALL_FRIEND_CONTINUING");
      final String id = listFriendID.get(index);
      FirebaseDatabase.getInstance().getReference().child("user/" + id).addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
          System.out.println("GET_ALL_FRIEND_RESPONSE");
          if (dataSnapshot.getValue() != null) {
            System.out.println("GET_ALL_FRIEND_RESPONSE_NOT_NULL");
            Friend user = new Friend();
            HashMap mapUserInfo = (HashMap) dataSnapshot.getValue();
            user.name = (String) mapUserInfo.get("name");
            user.phoneNumber = (String) mapUserInfo.get("phoneNumber");
            user.avata = (String) mapUserInfo.get("avata");
            user.id = id;
            user.idRoom = id.compareTo(StaticConfig.UID) > 0 ? (StaticConfig.UID + id).hashCode() + "" : "" + (id + StaticConfig.UID).hashCode();
            dataListFriend.getListFriend().add(user);
            FriendDB.getInstance(getContext()).addFriend(user);
          }
          getAllFriendInfo(index + 1);
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
          dialogFindAllFriend.dismiss();
        }
      });
    }
  }

  public Integer selectedPosition = -1;

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);

    selectedPosition = listViewPosFriend.get(v.toString().substring(28, 36));

    getActivity().getMenuInflater().inflate(R.menu.context_menu_friend, menu);
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {

    String friendName = listViewNameFriend.get(selectedPosition);

    switch (item.getItemId()) {
      case R.id.deleteFriend: {

        new AlertDialog.Builder(getContext())
          .setTitle("Удаление друга")
          .setMessage("Вы уверены что хотите удалить " + friendName + " из списка друзей?")
          .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
              dialogInterface.dismiss();
              final String idFriendRemoval = dataListFriend.getListFriend().get(selectedPosition).id;
              dialogWaitDeleting.setTitle("Удаление...")
                .setCancelable(false)
                .setTopColorRes(R.color.colorAccent)
                .show();
              deleteFriend(idFriendRemoval);
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
      case R.id.addBlackList: {
        new AlertDialog.Builder(getContext())
          .setTitle("Добавить в черный список")
          .setMessage("Вы уверены что хотите добавить в черный список " + friendName + "?")
          .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
              dialogInterface.dismiss();
              final String idFriendRemoval = dataListFriend.getListFriend().get(selectedPosition).id;
              dialogWaitDeleting.setTitle("Добавление в черный список...")
                .setCancelable(false)
                .setTopColorRes(R.color.colorAccent)
                .show();
              addToBlackList(idFriendRemoval);
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
      default: {

        return super.onContextItemSelected(item);
      }
    }
  }

  /**
   * Delete friend
   *
   * @param idFriend
   */
  private void deleteFriend(final String idFriend) {
    if (idFriend != null) {
      FirebaseDatabase.getInstance().getReference().child("friend").child(StaticConfig.UID)
        .orderByValue().equalTo(idFriend).addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {

          if (dataSnapshot.getValue() == null) {
            //phoneNumber not found
            dialogWaitDeleting.dismiss();
            new LovelyInfoDialog(getContext())
              .setTopColorRes(R.color.colorAccent)
              .setTitle("Ошибка")
              .setMessage("Не удалось удалить друга из списка друзей")
              .show();
          } else {
            String idRemoval = ((HashMap) dataSnapshot.getValue()).keySet().iterator().next().toString();
            FirebaseDatabase.getInstance().getReference().child("friend")
              .child(StaticConfig.UID).child(idRemoval).removeValue()
              .addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                  dialogWaitDeleting.dismiss();

                  new LovelyInfoDialog(getContext())
                    .setTopColorRes(R.color.colorAccent)
                    .setTitle("Успешно")
                    .setMessage("Друг удалён")
                    .show();

                  Intent intentDeleted = new Intent(FriendsFragment.ACTION_DELETE_FRIEND);
                  intentDeleted.putExtra("idFriend", idFriend);
                  getContext().sendBroadcast(intentDeleted);
                }
              })
              .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                  dialogWaitDeleting.dismiss();
                  new LovelyInfoDialog(getContext())
                    .setTopColorRes(R.color.colorAccent)
                    .setTitle("Ошибка")
                    .setMessage("Не удалось удалить друга из списка друзей")
                    .show();
                }
              });
          }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
      });
    } else {
      dialogWaitDeleting.dismiss();
      new LovelyInfoDialog(getContext())
        .setTopColorRes(R.color.red)
        .setTitle("Ошибка")
        .setMessage("Не удалось удалить друга из списка друзей")
        .show();
    }
  }

  private void addToBlackList(final String idFriend) {
    if (idFriend != null) {

      FirebaseDatabase.getInstance().getReference()
        .child("blacklist")
        .child(StaticConfig.UID)
        .push().setValue(idFriend)
        .addOnFailureListener(new OnFailureListener() {
          @Override
          public void onFailure(@NonNull Exception e) {
            dialogWaitDeleting.dismiss();
            new LovelyInfoDialog(getContext())
              .setTopColorRes(R.color.colorAccent)
              .setTitle("Ошибка")
              .setMessage("Не удалось добавить друга в черный список")
              .show();
          }
        })
        .addOnSuccessListener(new OnSuccessListener<Void>() {
          @Override
          public void onSuccess(Void aVoid) {
            dialogWaitDeleting.dismiss();

            new LovelyInfoDialog(getContext())
              .setTopColorRes(R.color.colorAccent)
              .setTitle("Успешно")
              .setMessage("Друг добавлен в черный список")
              .show();
          }
        });
    } else {
      dialogWaitDeleting.dismiss();
      new LovelyInfoDialog(getContext())
        .setTopColorRes(R.color.red)
        .setTitle("Ошибка")
        .setMessage("Не удалось добавить друга в черный список")
        .show();
    }
  }
}

class ListFriendsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private ListFriend listFriend;
  private Context context;
  public static Map<String, Query> mapQuery;
  public static Map<String, DatabaseReference> mapQueryOnline;
  public static Map<String, ChildEventListener> mapChildListener;
  public static Map<String, ChildEventListener> mapChildListenerOnline;
  public static Map<String, Boolean> mapMark;
  private FriendsFragment fragment;
  LovelyProgressDialog dialogWaitDeleting;

  public ListFriendsAdapter(Context context, ListFriend listFriend, FriendsFragment fragment) {
    this.listFriend = listFriend;
    this.context = context;
    mapQuery = new HashMap<>();
    mapChildListener = new HashMap<>();
    mapMark = new HashMap<>();
    mapChildListenerOnline = new HashMap<>();
    mapQueryOnline = new HashMap<>();
    this.fragment = fragment;
    dialogWaitDeleting = new LovelyProgressDialog(context);
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(context).inflate(R.layout.rc_item_friend, parent, false);
    return new ItemFriendViewHolder(context, view);
  }

  @Override
  public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
    final String name = friendsMap.containsKey(listFriend.getListFriend().get(position).phoneNumber) ? friendsMap.get(listFriend.getListFriend().get(position).phoneNumber) : listFriend.getListFriend().get(position).name;
    final String id = listFriend.getListFriend().get(position).id;
    final String idRoom = listFriend.getListFriend().get(position).idRoom;
    final String avata = listFriend.getListFriend().get(position).avata;
    ((ItemFriendViewHolder) holder).txtName.setText(name);

    fragment.registerForContextMenu((View) ((ItemFriendViewHolder) holder).txtName.getParent().getParent().getParent());
    fragment.listViewPosFriend.put(((ItemFriendViewHolder) holder).txtName.getParent().getParent().getParent().toString().substring(28, 36), position);
    fragment.listViewNameFriend.put(position, (String) ((ItemFriendViewHolder) holder).txtName.getText());

    ((View) ((ItemFriendViewHolder) holder).txtName.getParent().getParent().getParent())
      .setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          ((ItemFriendViewHolder) holder).txtMessage.setTypeface(Typeface.DEFAULT);
          ((ItemFriendViewHolder) holder).txtName.setTypeface(Typeface.DEFAULT);
          Intent intent = new Intent(context, ChatActivity.class);
          intent.putExtra(StaticConfig.INTENT_KEY_CHAT_FRIEND, name);
          ArrayList<CharSequence> idFriend = new ArrayList<CharSequence>();
          idFriend.add(id);
          intent.putCharSequenceArrayListExtra(StaticConfig.INTENT_KEY_CHAT_ID, idFriend);
          intent.putExtra(StaticConfig.INTENT_KEY_CHAT_ROOM_ID, idRoom);
          ChatActivity.bitmapAvataFriend = new HashMap<>();
          if (avata != null && !Objects.equals(avata, StaticConfig.STR_DEFAULT_BASE64)) {
            byte[] decodedString = Base64.decode(avata, Base64.DEFAULT);
            ChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length));
          } else {
            ChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avata));
          }

          mapMark.put(id, null);
          fragment.startActivityForResult(intent, FriendsFragment.ACTION_START_CHAT);
        }
      });

    if (listFriend.getListFriend().get(position).message.text != null && listFriend.getListFriend().get(position).message.text.length() > 0) {
      ((ItemFriendViewHolder) holder).txtMessage.setVisibility(View.VISIBLE);
      ((ItemFriendViewHolder) holder).txtTime.setVisibility(View.VISIBLE);
      if (!listFriend.getListFriend().get(position).message.text.startsWith(id)) {
        ((ItemFriendViewHolder) holder).txtMessage.setText(listFriend.getListFriend().get(position).message.text);
        ((ItemFriendViewHolder) holder).txtMessage.setTypeface(Typeface.DEFAULT);
        ((ItemFriendViewHolder) holder).txtName.setTypeface(Typeface.DEFAULT);
      } else {
        ((ItemFriendViewHolder) holder).txtMessage.setText(listFriend.getListFriend().get(position).message.text.substring((id + "").length()));
        ((ItemFriendViewHolder) holder).txtMessage.setTypeface(Typeface.DEFAULT_BOLD);
        ((ItemFriendViewHolder) holder).txtName.setTypeface(Typeface.DEFAULT_BOLD);
      }
      String time = new SimpleDateFormat("EEE, d MMM yyyy").format(new Date(listFriend.getListFriend().get(position).message.timestamp));
      String today = new SimpleDateFormat("EEE, d MMM yyyy").format(new Date(System.currentTimeMillis()));
      if (today.equals(time)) {
        ((ItemFriendViewHolder) holder).txtTime.setText(new SimpleDateFormat("HH:mm").format(new Date(listFriend.getListFriend().get(position).message.timestamp)));
      } else {
        ((ItemFriendViewHolder) holder).txtTime.setText(new SimpleDateFormat("MMM d").format(new Date(listFriend.getListFriend().get(position).message.timestamp)));
      }
    } else {
      ((ItemFriendViewHolder) holder).txtMessage.setVisibility(View.GONE);
      ((ItemFriendViewHolder) holder).txtTime.setVisibility(View.GONE);
      if (mapQuery.get(id) == null && mapChildListener.get(id) == null) {
        mapQuery.put(id, FirebaseDatabase.getInstance().getReference().child("message/" + idRoom).limitToLast(1));
        mapChildListener.put(id, new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot dataSnapshot, String s) {
            HashMap mapMessage = (HashMap) dataSnapshot.getValue();

            if (position < listFriend.getListFriend().size()) {
              if (listFriend.getListFriend() != null && listFriend.getListFriend().get(position).message != null &&
                listFriend.getListFriend().get(position).message.text != null &&
                listFriend.getListFriend() != null) {
                if (mapMark.get(id) != null) {
                  if (!mapMark.get(id)) {
                    listFriend.getListFriend().get(position).message.text = id + mapMessage.get("text");
                  } else {
                    listFriend.getListFriend().get(position).message.text = (String) mapMessage.get("text");
                  }
                  notifyDataSetChanged();
                  mapMark.put(id, false);
                } else {
                  listFriend.getListFriend().get(position).message.text = (String) mapMessage.get("text");
                  notifyDataSetChanged();
                }
              }

              if (listFriend.getListFriend().get(position).message != null &&
                listFriend.getListFriend().get(position).message.fileModel != null &&
                listFriend.getListFriend() != null) {

                listFriend.getListFriend().get(position).message.fileModel = (FileModel) mapMessage.get("fileModel");
                notifyDataSetChanged();

              }

              //TODO for fileModel

              listFriend.getListFriend().get(position).message.timestamp = (long) mapMessage.get("timestamp");
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
        mapQuery.get(id).addChildEventListener(mapChildListener.get(id));
        mapMark.put(id, true);
      } else {
        mapQuery.get(id).removeEventListener(mapChildListener.get(id));
        mapQuery.get(id).addChildEventListener(mapChildListener.get(id));
        mapMark.put(id, true);
      }
    }
    if (StaticConfig.STR_DEFAULT_BASE64.equals(listFriend.getListFriend().get(position).avata)) {
      ((ItemFriendViewHolder) holder).avata.setImageResource(R.drawable.default_avata);
    } else {

      if (listFriend.getListFriend().get(position).avata != null) {
        byte[] decodedString = Base64.decode(listFriend.getListFriend().get(position).avata, Base64.DEFAULT);
        Bitmap src = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        ((ItemFriendViewHolder) holder).avata.setImageBitmap(src);
      }
    }


    if (mapQueryOnline.get(id) == null && mapChildListenerOnline.get(id) == null) {
      mapQueryOnline.put(id, FirebaseDatabase.getInstance().getReference().child("user/" + id + "/status"));
      mapChildListenerOnline.put(id, new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
          if (dataSnapshot.getValue() != null && dataSnapshot.getKey().equals("isOnline")) {
            Log.d("FriendsFragment add " + id, (boolean) dataSnapshot.getValue() + "");
            listFriend.getListFriend().get(position).status.isOnline = (boolean) dataSnapshot.getValue();
            notifyDataSetChanged();
          }
        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String s) {
          if (dataSnapshot.getValue() != null && dataSnapshot.getKey().equals("isOnline")) {
            Log.d("FriendsFragment change " + id, (boolean) dataSnapshot.getValue() + "");
            listFriend.getListFriend().get(position).status.isOnline = (boolean) dataSnapshot.getValue();
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

    if (listFriend.getListFriend().get(position).status.isOnline) {
      ((ItemFriendViewHolder) holder).avata.setBorderWidth(10);
    } else {
      ((ItemFriendViewHolder) holder).avata.setBorderWidth(0);
    }
  }

  @Override
  public int getItemCount() {
    return listFriend != null ? (listFriend.getListFriend() != null ? listFriend.getListFriend().size() : 0) : 0;
  }
}

class ItemFriendViewHolder extends RecyclerView.ViewHolder {
  public CircleImageView avata;
  public TextView txtName, txtTime, txtMessage;
  private Context context;

  ItemFriendViewHolder(Context context, View itemView) {
    super(itemView);
    avata = (CircleImageView) itemView.findViewById(R.id.icon_avata);
    txtName = (TextView) itemView.findViewById(R.id.txtName);
    txtTime = (TextView) itemView.findViewById(R.id.txtTime);
    txtMessage = (TextView) itemView.findViewById(R.id.txtMessage);
    this.context = context;
  }
}

