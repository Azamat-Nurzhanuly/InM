package com.android.barracuda.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.barracuda.R;
import com.android.barracuda.data.CallDB;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.Call;
import com.android.barracuda.model.ListCall;
import com.android.barracuda.service.SinchService;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.yarolegovich.lovelydialog.LovelyProgressDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import de.hdodenhof.circleimageview.CircleImageView;

import static com.android.barracuda.data.StaticConfig.CALL_INCOMING;
import static com.android.barracuda.data.StaticConfig.CALL_OUTGOING;

public class CallListFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

  private RecyclerView recyclerListCalls;
  private ListCallAdapter adapter;
  private ListCall dataListCalls = null;
  private ArrayList<String> listFriendID = null;
  private LovelyProgressDialog dialogFindAllCalls;
  private SwipeRefreshLayout mSwipeRefreshLayout;

  public static int ACTION_START_CALL = 1;


  @Override
  public void onCreate(Bundle savedInstanceState) {
    dataListCalls = CallDB.getInstance(getContext()).getListCall();
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {

    if (dataListCalls == null) {
      dataListCalls = CallDB.getInstance(getContext()).getListCall();
      if (dataListCalls.getListCall().size() > 0) {
        listFriendID = new ArrayList<>();
        for (Call call : dataListCalls.getListCall()) {
          listFriendID.add(call.id);
        }
      }
    }
    View layout = inflater.inflate(R.layout.fragment_people, container, false);
    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
    recyclerListCalls = (RecyclerView) layout.findViewById(R.id.recycleListFriend);
    recyclerListCalls.setLayoutManager(linearLayoutManager);
    mSwipeRefreshLayout = (SwipeRefreshLayout) layout.findViewById(R.id.swipeRefreshLayout);
    mSwipeRefreshLayout.setOnRefreshListener(this);
    adapter = new ListCallAdapter(getContext(), dataListCalls, this);
    recyclerListCalls.setAdapter(adapter);
//    dialogFindAllCalls = new LovelyProgressDialog(getContext());
//    if (listFriendID == null) {
//      listFriendID = new ArrayList<>();
//      dialogFindAllCalls.setCancelable(false)
//        .setIcon(R.drawable.ic_add_friend)
//        .setTitle("Все звонки....")
//        .setTopColorRes(R.color.colorPrimary)
//        .show();
//
//      mSwipeRefreshLayout.setRefreshing(false);
//    }


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

  @Override
  public void onRefresh() {
    dataListCalls = CallDB.getInstance(getContext()).getListCall();
    ListCallAdapter adapter = new ListCallAdapter(getContext(), dataListCalls, this);


    recyclerListCalls.setAdapter(adapter);

//    dataListCalls = CallDB.getInstance(getContext()).getListCall();


    mSwipeRefreshLayout.setRefreshing(false);
  }

  private void getAllCallInfo(final int index) {
    if (index == listFriendID.size()) {
      //save list friend
      adapter.notifyDataSetChanged();
      dialogFindAllCalls.dismiss();
      mSwipeRefreshLayout.setRefreshing(false);
    } else {
      final String id = listFriendID.get(index);
      FirebaseDatabase.getInstance().getReference().child("user/" + id).addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
          if (dataSnapshot.getValue() != null) {
            Call call = new Call();
            HashMap mapUserInfo = (HashMap) dataSnapshot.getValue();
            call.name = (String) mapUserInfo.get("name");
            call.phoneNumber = (String) mapUserInfo.get("phoneNumber");
            call.avata = (String) mapUserInfo.get("avata");
            call.id = id;
            call.callId = String.valueOf(new Date().getTime());
            dataListCalls.getListCall().add(call);
            CallDB.getInstance(getContext()).addCall(call);
          }
          getAllCallInfo(index + 1);
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
      });
    }
  }
}

class ListCallAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private ListCall listCall;
  private Context context;
  private CallListFragment fragment;
  LovelyProgressDialog dialogWaitDeleting;

  public ListCallAdapter(Context context, ListCall listCall, CallListFragment fragment) {
    this.listCall = listCall;
    this.context = context;


    this.fragment = fragment;
    dialogWaitDeleting = new LovelyProgressDialog(context);
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(context).inflate(R.layout.item_incoming_call, parent, false);
    return new ItemCallViewHolder(context, view);
  }

  @Override
  public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position) {
    {
      final String name = listCall.getListCall().get(position).name;
      final String id = listCall.getListCall().get(position).id;
      final String avata = listCall.getListCall().get(position).avata;
      final String type = listCall.getListCall().get(position).type;

      ((ItemCallViewHolder) holder).txtName.setText(name);

      ((View) ((ItemCallViewHolder) holder).txtName.getParent().getParent().getParent())
        .setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View view) {
//                  ((ItemCallViewHolder) holder).txtName.setTypeface(Typeface.DEFAULT);
//                  Intent intent = new Intent(context, ChatActivity.class);
//                  intent.putExtra(StaticConfig.INTENT_KEY_CHAT_FRIEND, name);
//                  ArrayList<CharSequence> idFriend = new ArrayList<CharSequence>();
//                  idFriend.add(id);
//                  intent.putCharSequenceArrayListExtra(StaticConfig.INTENT_KEY_CHAT_ID, idFriend);
//                  ChatActivity.bitmapAvataFriend = new HashMap<>();
//                  if (!avata.equals(StaticConfig.STR_DEFAULT_BASE64)) {
//                    byte[] decodedString = Base64.decode(avata, Base64.DEFAULT);
//                    ChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length));
//                  } else {
//                    ChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avata));
//                  }
//
//                  mapMark.put(id, null);
//                  fragment.startActivityForResult(intent, FriendsFragment.ACTION_START_CHAT);
          }
        });

      ((View) ((ItemCallViewHolder) holder).txtName.getParent().getParent().getParent())
        .setOnLongClickListener(new View.OnLongClickListener() {

          @Override
          public boolean onLongClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(name)
              .setItems(R.array.call_list_array, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                  //0 = audio
                  //1 = video
                  switch (which) {
                    case 0:
                      audioCall();
                    case 1:
                      break;
                  }
                }


                private void audioCall() {

                  //TODO AUDIO CALL
                  Intent callScreen = new Intent(context, CallScreenActivity.class);
                  callScreen.putExtra(SinchService.CALL_ID, "");
                  fragment.startActivityForResult(callScreen, CallListFragment.ACTION_START_CALL);


                }
              }).show();
            return true;
          }
        });

      if (listCall.getListCall().get(position) != null) {

        if (CALL_OUTGOING.equalsIgnoreCase(type)) {
          ((ItemCallViewHolder) holder).type.setImageResource(R.drawable.out_call_history);
        } else {
          if (CALL_INCOMING.equalsIgnoreCase(type)) {
            ((ItemCallViewHolder) holder).type.setImageResource(R.drawable.inc_call_history);
          }
        }

        ((ItemCallViewHolder) holder).txtTime.setVisibility(View.VISIBLE);
        if (!listCall.getListCall().get(position).message.text.startsWith(id)) {
          ((ItemCallViewHolder) holder).txtName.setTypeface(Typeface.DEFAULT);
        } else {
          ((ItemCallViewHolder) holder).txtName.setTypeface(Typeface.DEFAULT_BOLD);
        }
        String time = new SimpleDateFormat("EEE, d MMM yyyy").format(new Date(listCall.getListCall().get(position).message.timestamp));
        String today = new SimpleDateFormat("EEE, d MMM yyyy").format(new Date(System.currentTimeMillis()));
        if (today.equals(time)) {
          ((ItemCallViewHolder) holder).txtTime.setText(new SimpleDateFormat("HH:mm").format(new Date(listCall.getListCall().get(position).message.timestamp)));
        } else {
          ((ItemCallViewHolder) holder).txtTime.setText(new SimpleDateFormat("MMM d").format(new Date(listCall.getListCall().get(position).message.timestamp)));
        }
      } else {
        ((ItemCallViewHolder) holder).txtTime.setVisibility(View.GONE);
      }
      if (StaticConfig.STR_DEFAULT_BASE64.equals(listCall.getListCall().get(position).avata)) {
        ((ItemCallViewHolder) holder).avata.setImageResource(R.drawable.default_avata);
      } else {

        if (listCall.getListCall().get(position).avata != null) {
          byte[] decodedString = Base64.decode(listCall.getListCall().get(position).avata, Base64.DEFAULT);
          Bitmap src = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
          ((ItemCallViewHolder) holder).avata.setImageBitmap(src);
        }
      }
    }
  }


  @Override
  public int getItemCount() {
    return listCall.getListCall() != null ? listCall.getListCall().size() : 0;
  }
}

class ItemCallViewHolder extends RecyclerView.ViewHolder {
  public CircleImageView avata;
  public ImageView type;
  public TextView txtName, txtTime;
  private Context context;

  ItemCallViewHolder(Context context, View itemView) {
    super(itemView);
    avata = (CircleImageView) itemView.findViewById(R.id.icon_avata);
    type = (ImageView) itemView.findViewById(R.id.type);
    txtName = (TextView) itemView.findViewById(R.id.txtName);
    txtTime = (TextView) itemView.findViewById(R.id.txtTime);
    this.context = context;
  }
}

