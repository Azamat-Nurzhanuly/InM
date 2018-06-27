package com.android.barracuda.ui;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.barracuda.MainActivity;
import com.android.barracuda.R;
import com.android.barracuda.data.FriendDB;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.ContactModel;
import com.android.barracuda.model.Friend;
import com.android.barracuda.model.ListContact;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.yarolegovich.lovelydialog.LovelyInfoDialog;
import com.yarolegovich.lovelydialog.LovelyProgressDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;

public class ContactListFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

  private RecyclerView recyclerListContacts;
  private ListContactAdapter adapter;
  private ListContact dataContactList = null;
  private ArrayList<String> listFriendID = null;
  private LovelyProgressDialog dialogFindAllCalls;
  private SwipeRefreshLayout mSwipeRefreshLayout;


  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    getAllPhoneContacts();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onStart() {
    super.onStart();

    adapter = new ListContactAdapter(getContext(), dataContactList, this);
    recyclerListContacts.setAdapter(adapter);
  }

  @Override
  public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {

    View layout = inflater.inflate(R.layout.fragment_contact, container, false);
    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
    recyclerListContacts = (RecyclerView) layout.findViewById(R.id.recycleListFriend);
    recyclerListContacts.setLayoutManager(linearLayoutManager);
    mSwipeRefreshLayout = (SwipeRefreshLayout) layout.findViewById(R.id.swipeRefreshLayout);
    mSwipeRefreshLayout.setOnRefreshListener(this);


    return layout;
  }


  @Override
  public void onDestroyView() {
    super.onDestroyView();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
  }

  public void getAllPhoneContacts() {
    dataContactList = new ListContact();
    Log.d("START", "Getting all Contacts");
    Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
    Cursor cursor = Objects.requireNonNull(getContext())
      .getContentResolver()
      .query(uri, new String[]
        {
          ContactsContract.CommonDataKinds.Phone.NUMBER,
          ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
          ContactsContract.CommonDataKinds.Phone._ID
        }, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

    assert cursor != null;
    cursor.moveToFirst();
    while (!cursor.isAfterLast()) {
      String contactNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
      String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
      int phoneContactID = cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID));


      ContactModel phoneContactInfo = new ContactModel();
      phoneContactInfo.id = String.valueOf(phoneContactID);
      phoneContactInfo.name = contactName;

      contactNumber = contactNumber != null ? contactNumber.replaceAll("\\s", "").replaceAll("[^0-9]", "") : "";

      phoneContactInfo.number = contactNumber;
      if (phoneContactInfo.number != null) {
        dataContactList.getUniqueListContact().add(phoneContactInfo);
        findIDPhoneNumber(phoneContactInfo);
      }
      cursor.moveToNext();
    }
    cursor.close();
    cursor = null;
    Log.d("END", "Got all Contacts");
  }

  private void findIDPhoneNumber(final ContactModel contact) {

    contact.number = contact.number.replaceAll("[\\+ \\(\\)]", "");
    final boolean[] result = {false};
    if (contact.number.length() > 10) {
      contact.number = contact.number.substring(contact.number.length() - 10);
    }

    FirebaseDatabase.getInstance().getReference().child("user")
      .orderByChild("phoneNumber").equalTo(contact.number)
      .addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
          if (dataSnapshot.getValue() != null) {
            dataContactList.getListContact().add(contact);

            if (adapter != null) {
              adapter.notifyDataSetChanged();
            }
          }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
      });
  }

  @Override
  public void onRefresh() {

    //TODO REFRESH
    ListContactAdapter adapter = new ListContactAdapter(getContext(), dataContactList, this);
    recyclerListContacts.setAdapter(adapter);
    mSwipeRefreshLayout.setRefreshing(false);
  }
}

class ListContactAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private ListContact listContact;
  private Context context;
  private ContactListFragment fragment;

  private LovelyProgressDialog dialogWait;


  public ListContactAdapter(Context context, ListContact listContact, ContactListFragment fragment) {
    this.listContact = listContact;
    this.context = context;


    this.fragment = fragment;
    dialogWait = new LovelyProgressDialog(context);
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(context).inflate(R.layout.row_contact, parent, false);
    return new ItemContactViewHolder(context, view);
  }

  @Override
  public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position) {

    if (holder instanceof ItemContactViewHolder) {
      if (((ItemContactViewHolder) (holder)).contactNumber != null && ((ItemContactViewHolder) (holder)).contactName != null) {
        ((ItemContactViewHolder) (holder)).contactNumber.setText(listContact.getListContact().get(position).number);
        ((ItemContactViewHolder) (holder)).contactName.setText(listContact.getListContact().get(position).name);
      }


      holder.itemView.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {

          goToFriend(listContact.getListContact().get(position).number);
        }
      });

    }
  }

  private void goToFriend(String phoneNumber) {

    int themeColor = MainActivity.getThemeColor((MainActivity) context);

    phoneNumber = phoneNumber.replaceAll("[\\+ \\(\\)]", "");

    if (phoneNumber.length() > 10) {
      phoneNumber = phoneNumber.substring(phoneNumber.length() - 10);
    }

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
            .setMessage("Номер телефона не найден")
            .show();
        } else {
          String id = ((HashMap) dataSnapshot.getValue()).keySet().iterator().next().toString();
          if (id.equals(StaticConfig.UID)) {
            new LovelyInfoDialog(context)
              .setTopColorRes(R.color.colorAccent)
              .setIcon(R.drawable.ic_add_friend)
              .setTitle("Ошибка")
              .setMessage("Номер телефона неверный")
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

      private void checkBeforeAddFriend(final String idFriend, Friend userInfo) {

        int themeColor = MainActivity.getThemeColor((MainActivity) context);

        addFriend(idFriend, true, userInfo);
        FriendDB.getInstance(context).addFriend(userInfo);
      }

      private void addFriend(final String idFriend, boolean isIdFriend, final Friend userInfo) {
        if (idFriend != null) {
          if (isIdFriend) {
            FirebaseDatabase.getInstance().getReference().child("friend/" + StaticConfig.UID).push().setValue(idFriend)
              .addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                  if (task.isSuccessful()) {
                    addFriend(idFriend, false, userInfo);
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
                    .setMessage("Ошибка добавления друга")
                    .show();
                }
              });
          } else {
            FirebaseDatabase.getInstance().getReference().child("friend/" + idFriend).push().setValue(StaticConfig.UID).addOnCompleteListener(new OnCompleteListener<Void>() {
              @Override
              public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                  addFriend(null, false, userInfo);
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
                    .setMessage("Ошибка добавления друга")
                    .show();
                }
              });
          }
        } else {
          dialogWait.dismiss();

          int themeColor = MainActivity.getThemeColor((MainActivity) context);

          new LovelyInfoDialog(context)
            .setTopColorRes(themeColor)
            .setIcon(R.drawable.ic_add_friend)
            .setTitle("Успешно")
            .setMessage("Номер добавлен в список друзей")
            .show();

          Intent intent = new Intent(context, ChatActivity.class);
          intent.putExtra(StaticConfig.INTENT_KEY_CHAT_FRIEND, userInfo.name);
          ArrayList<CharSequence> idFriendList = new ArrayList<CharSequence>();
          idFriendList.add(userInfo.id);
          intent.putCharSequenceArrayListExtra(StaticConfig.INTENT_KEY_CHAT_ID, idFriendList);
          intent.putExtra(StaticConfig.INTENT_KEY_CHAT_ROOM_ID, userInfo.idRoom);
          ChatActivity.bitmapAvataFriend = new HashMap<>();
          if (userInfo.avata != null && !Objects.equals(userInfo.avata, StaticConfig.STR_DEFAULT_BASE64)) {
            byte[] decodedString = Base64.decode(userInfo.avata, Base64.DEFAULT);
            ChatActivity.bitmapAvataFriend.put(userInfo.id, BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length));
          } else {
            ChatActivity.bitmapAvataFriend.put(userInfo.id, BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avata));
          }

          ((MainActivity) context).startActivityForResult(intent, FriendsFragment.ACTION_START_CHAT);
        }
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {

      }
    });
  }


  @Override
  public int getItemCount() {
    return listContact.getListContact() != null ? listContact.getListContact().size() : 0;
  }
}

class ItemContactViewHolder extends RecyclerView.ViewHolder {
  public CircleImageView avata;
  public ImageView type;
  public TextView contactNumber, contactName;
  private Context context;

  ItemContactViewHolder(Context context, View itemView) {
    super(itemView);
    avata = (CircleImageView) itemView.findViewById(R.id.icon_avata);
    type = (ImageView) itemView.findViewById(R.id.type);
    contactNumber = (TextView) itemView.findViewById(R.id.contact_number);
    contactName = (TextView) itemView.findViewById(R.id.contact_username);
    this.context = context;
  }
}

