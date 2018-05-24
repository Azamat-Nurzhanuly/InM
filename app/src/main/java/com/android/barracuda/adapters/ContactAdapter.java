package com.android.barracuda.adapters;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.barracuda.R;

import org.w3c.dom.Text;

public class ContactAdapter extends CursorRecyclerViewAdapter<ContactAdapter.ContactsViewHolder> {

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

    String username = cursor.getString(cursor.getColumnIndex(
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
        ContactsContract.Data.DISPLAY_NAME_PRIMARY : ContactsContract.Data
        .DISPLAY_NAME
    ));

    String number = cursor.getString(cursor.getColumnIndex(
      ContactsContract.CommonDataKinds.Phone.NUMBER
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
