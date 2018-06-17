package com.android.barracuda.adapters;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.barracuda.MainActivity;
import com.android.barracuda.R;
import com.android.barracuda.data.FriendDB;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.Friend;
import com.android.barracuda.ui.ChatActivity;
import com.android.barracuda.ui.FriendsFragment;
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

public class ContactAdapter extends CursorRecyclerViewAdapter<ContactAdapter.ContactsViewHolder> {

  private LovelyProgressDialog dialogWait;
//  private ArrayList<String> listFriendID = null;

  public ContactAdapter(Context context, Cursor cursor, String id) {

    super(context, cursor, id);
  }

  @Override
  public ContactsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext())
      .inflate(R.layout.row_contact, parent, false);
    return new ContactsViewHolder(view);
  }

  @Override
  public void onBindViewHolder(ContactsViewHolder viewHolder, Cursor cursor) {

    String number = "";

    while (cursor.moveToNext())
    {
      int phoneType = cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE));
      if (phoneType == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
      {
        number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DATA));
        number = number.replaceAll("\\s", "");

        number = number.replaceAll("[^0-9]", "");

        if(number.length() != 11) {

          falseContacts.add(number);
          continue;
        }

        break;
      }
    }

    final String username = cursor.getString(cursor.getColumnIndex(
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
        ContactsContract.Data.DISPLAY_NAME_PRIMARY : ContactsContract.Data
        .DISPLAY_NAME
    ));

    viewHolder.setUsername(username);
    viewHolder.setNumber(number);
    long contactId = getItemId(cursor.getPosition());
    long photoId = cursor.getLong(cursor.getColumnIndex(
      ContactsContract.Data.PHOTO_ID
    ));

    if (photoId != 0) {
      Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,
        contactId);
      Uri photUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo
        .CONTENT_DIRECTORY);
      viewHolder.imageViewContactDisplay.setImageURI(photUri);
    } else
      viewHolder.imageViewContactDisplay.setImageResource(R.drawable.default_avata);

    final String phoneNumber = number;

    viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {

        dialogWait = new LovelyProgressDialog(mContext);

        findIDPhoneNumber(phoneNumber);
      }
    });
  }

  private void findIDPhoneNumber(String phoneNumber) {

    int themeColor = MainActivity.getThemeColor((MainActivity) mContext);

    phoneNumber = phoneNumber.replaceAll("[\\+ \\(\\)]", "");

    if(phoneNumber.length() > 10) {
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
          new LovelyInfoDialog(mContext)
            .setTopColorRes(R.color.colorAccent)
            .setIcon(R.drawable.ic_add_friend)
            .setTitle("Ошибка")
            .setMessage("Номер телефона не найден")
            .show();
        } else {
          String id = ((HashMap) dataSnapshot.getValue()).keySet().iterator().next().toString();
          if (id.equals(StaticConfig.UID)) {
            new LovelyInfoDialog(mContext)
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

        int themeColor = MainActivity.getThemeColor((MainActivity) mContext);

        addFriend(idFriend, true, userInfo);
        FriendDB.getInstance(mContext).addFriend(userInfo);
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
                  new LovelyInfoDialog(mContext)
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
                  new LovelyInfoDialog(mContext)
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

          int themeColor = MainActivity.getThemeColor((MainActivity) mContext);

          new LovelyInfoDialog(mContext)
            .setTopColorRes(themeColor)
            .setIcon(R.drawable.ic_add_friend)
            .setTitle("Успешно")
            .setMessage("Номер добавлен в список друзей")
            .show();

          Intent intent = new Intent(mContext, ChatActivity.class);
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
            ChatActivity.bitmapAvataFriend.put(userInfo.id, BitmapFactory.decodeResource(mContext.getResources(), R.drawable.default_avata));
          }

          ((MainActivity) mContext).startActivityForResult(intent, FriendsFragment.ACTION_START_CHAT);
        }
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {

      }
    });
  }

  public static class ContactsViewHolder extends RecyclerView.ViewHolder {
    TextView textViewContactUsername;
    TextView textViewContactNumber;
    ImageView imageViewContactDisplay;

    public ContactsViewHolder(View itemView) {
      super(itemView);
      textViewContactUsername = (TextView) itemView.findViewById(R.id
        .contact_username);
      imageViewContactDisplay = (ImageView) itemView.findViewById(R.id
        .image_view_contact_display);
      textViewContactNumber = (TextView) itemView.findViewById(R.id.contact_number);
    }

    public void setUsername(String username) {
      textViewContactUsername.setText(username);
    }

    public void setNumber(String number) {
      textViewContactNumber.setText(number);
    }
  }
}
