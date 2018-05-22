package com.android.barracuda.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.android.barracuda.R;

import com.android.barracuda.ui.dummy.DummyContent;
import com.android.barracuda.ui.dummy.DummyContent.DummyItem;

import java.util.List;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class ContactsFragment extends Fragment {

  // TODO: Customize parameter argument names
  private static final String ARG_COLUMN_COUNT = "column-count";
  // TODO: Customize parameters
  private int mColumnCount = 1;
  private OnListFragmentInteractionListener mListener;

  /*
     * Defines an array that contains column names to move from
     * the Cursor to the ListView.
     */
  @SuppressLint("InlinedApi")
  private final static String[] FROM_COLUMNS = {
    Build.VERSION.SDK_INT
      >= Build.VERSION_CODES.HONEYCOMB ?
      ContactsContract.Contacts.DISPLAY_NAME_PRIMARY :
      ContactsContract.Contacts.DISPLAY_NAME
  };
  /*
   * Defines an array that contains resource ids for the layout views
   * that get the Cursor column contents. The id is pre-defined in
   * the Android framework, so it is prefaced with "android.R.id"
   */
  private final static int[] TO_IDS = {
    android.R.id.text1
  };
  // Define global mutable variables
  // Define a ListView object
  ListView mContactsList;
  // Define variables for the contact the user selects
  // The contact's _ID value
  long mContactId;
  // The contact's LOOKUP_KEY
  String mContactKey;
  // A content URI for the selected contact
  Uri mContactUri;
  // An adapter that binds the result Cursor to the ListView
  private SimpleCursorAdapter mCursorAdapter;

  /**
   * Mandatory empty constructor for the fragment manager to instantiate the
   * fragment (e.g. upon screen orientation changes).
   */
  public ContactsFragment() {
  }

  // TODO: Customize parameter initialization
  @SuppressWarnings("unused")
  public static ContactsFragment newInstance(int columnCount) {
    ContactsFragment fragment = new ContactsFragment();
    Bundle args = new Bundle();
    args.putInt(ARG_COLUMN_COUNT, columnCount);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (getArguments() != null) {
      mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_contacts_list, container, false);

    // Set the adapter
    if (view instanceof RecyclerView) {
      Context context = view.getContext();
      RecyclerView recyclerView = (RecyclerView) view;
      if (mColumnCount <= 1) {
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
      } else {
        recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
      }
      recyclerView.setAdapter(new MyContactsRecyclerViewAdapter(DummyContent.ITEMS, mListener));
    }

    return view;
  }


  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (context instanceof OnListFragmentInteractionListener) {
      mListener = (OnListFragmentInteractionListener) context;
    } else {
      throw new RuntimeException(context.toString()
        + " must implement OnListFragmentInteractionListener");
    }
  }

  @Override
  public void onDetach() {
    super.onDetach();
    mListener = null;
  }

  /**
   * This interface must be implemented by activities that contain this
   * fragment to allow an interaction in this fragment to be communicated
   * to the activity and potentially other fragments contained in that
   * activity.
   * <p/>
   * See the Android Training lesson <a href=
   * "http://developer.android.com/training/basics/fragments/communicating.html"
   * >Communicating with Other Fragments</a> for more information.
   */
  public interface OnListFragmentInteractionListener {
    // TODO: Update argument type and name
    void onListFragmentInteraction(DummyItem item);
  }
}
