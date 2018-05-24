package com.android.barracuda;

import android.Manifest;
import android.app.FragmentTransaction;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.android.barracuda.adapters.ContactAdapter;

public class ContactsActivity extends BarracudaActivity {

//  ListView contactsList;
  private static final int REQUEST_PERMISSION = 2001;
  private static final int CONTENT_VIEW_ID = 10101010;
  PlaceholderFragment contactFragment;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setTheme();

    getSupportActionBar().setTitle("Контакты");

    ActionBar ab = getSupportActionBar();
    assert ab != null;
    ab.setDisplayHomeAsUpEnabled(true);

    setContentView(R.layout.activity_contacts);

//    contactsList = (ListView) findViewById(R.id.contacts);
//    showContacts();
  }

//  private List<String> getContactNames() {
//    List<String> contacts = new ArrayList<>();
//    // Get the ContentResolver
//    ContentResolver cr = getContentResolver();
//    // Get the Cursor of all the contacts
//    Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
//
//    // Move the cursor to first. Also check whether the cursor is empty or not.
//    if (cursor.moveToFirst()) {
//      // Iterate through the cursor
//      do {
//        // Get the contacts name
//
//        String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
//        contacts.add(name);
//      } while (cursor.moveToNext());
//    }
//    // Close the curosor
//    cursor.close();
//
//    return contacts;
//  }
//
//  // Request code for READ_CONTACTS. It can be any number > 0.
//  private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 100;
//
//  /**
//   * Show the contacts in the ListView.
//   */
//  private void showContacts() {
//    // Check the SDK version and whether the permission is already granted or not.
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
//      requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, PERMISSIONS_REQUEST_READ_CONTACTS);
//      //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method
//    } else {
//      // Android version is lesser than 6.0 or the permission is already granted.
//      List<String> contacts = getContactNames();
//      ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, contacts);
//      contactsList.setAdapter(adapter);
//    }
//  }
//
//  @Override
//  public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                                         int[] grantResults) {
//    if (requestCode == PERMISSIONS_REQUEST_READ_CONTACTS) {
//      if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//        // Permission is granted
//        showContacts();
//      } else {
//        Toast.makeText(this, "Until you grant the permission, we canot display the names", Toast.LENGTH_SHORT).show();
//      }
//    }
//  }

  public static class PlaceholderFragment extends Fragment implements
    LoaderManager.LoaderCallbacks<Cursor> {
    private static final int LOADER_ID = 1;
    private RecyclerView contactsRecycler;
    private ContactAdapter mContactAdapter;

    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private View mRootView;

    private static final String[] FROM_COLUMNS = {
      ContactsContract.Data.CONTACT_ID,
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
        ContactsContract.Data.DISPLAY_NAME_PRIMARY : ContactsContract.Data
        .DISPLAY_NAME,
      ContactsContract.Data.PHOTO_ID
    };
    private Bundle mBundle;

    public PlaceholderFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
      mRootView = inflater.inflate(R.layout.fragment_contacts, container, false);

      mBundle = savedInstanceState;
      if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_CONTACTS) !=
        PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
          getActivity(),
          new String[]{
            Manifest.permission.READ_CONTACTS
          },
          REQUEST_PERMISSION
        );
      } else {
        getLoaderManager().initLoader(LOADER_ID, savedInstanceState, this);
      }
      init();


      return mRootView;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
      if (requestCode == REQUEST_PERMISSION) {
        if (grantResults.length != 0) {
          if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLoaderManager().initLoader(LOADER_ID, mBundle, this);
          } else {
            getActivity().finish();
          }
        }
      }
    }

    private void init() {
      contactsRecycler = (RecyclerView) mRootView.findViewById(R.id.contacts);
      contactsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
      contactsRecycler.setHasFixedSize(true);

      mContactAdapter = new ContactAdapter(getContext(), null, ContactsContract.Data
        .CONTACT_ID);
      contactsRecycler.setAdapter(mContactAdapter);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
      switch (id) {
        case LOADER_ID:
          return new CursorLoader(
            getContext(),
            ContactsContract.Data.CONTENT_URI,
            FROM_COLUMNS,
            null,
            null,
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
              ContactsContract.Data.DISPLAY_NAME_PRIMARY : ContactsContract.Data
              .DISPLAY_NAME) +
              " ASC"
          );
        default:
          if (BuildConfig.DEBUG)
            throw new IllegalArgumentException("no id handled!");
          return null;
      }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
      mContactAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
      mContactAdapter.swapCursor(null);
    }
  }
}
