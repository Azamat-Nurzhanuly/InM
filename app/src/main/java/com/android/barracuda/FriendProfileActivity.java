package com.android.barracuda;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.barracuda.data.SharedPreferenceHelper;
import com.android.barracuda.model.Configuration;
import com.android.barracuda.model.User;
import com.android.barracuda.util.ImageUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.yarolegovich.lovelydialog.LovelyProgressDialog;

import java.util.ArrayList;
import java.util.List;

import static com.android.barracuda.MainActivity.friendsMap;

public class FriendProfileActivity extends BarracudaActivity {

  public String friend_id = null;
  DatabaseReference friendDB;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    friend_id = getIntent().getStringExtra("friend_id");

    setTheme();

    setContentView(R.layout.activity_profile);

    ActionBar ab = getSupportActionBar();
    assert ab != null;
    ab.setDisplayHomeAsUpEnabled(true);

    friendDB = FirebaseDatabase.getInstance().getReference().child("user").child(friend_id);
    friendDB.addListenerForSingleValueEvent(userListener);
    mAuth = FirebaseAuth.getInstance();

    context = this;
    activity = this;
    avatar = (ImageView) findViewById(R.id.img_avatar);
    tvUserName = (TextView) findViewById(R.id.tv_username);

    int themeColor = MainActivity.getThemeColor(activity);
    View profileInfoHolder = findViewById(R.id.profile);
    profileInfoHolder.setBackgroundColor(themeColor);

    recyclerView = (RecyclerView) findViewById(R.id.info_recycler_view);
    infoAdapter = new UserInfoAdapter(listConfig);
    RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(context);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setItemAnimator(new DefaultItemAnimator());
    recyclerView.setAdapter(infoAdapter);

    waitingDialog = new LovelyProgressDialog(context);
  }

  TextView tvUserName;
  ImageView avatar;

  private List<Configuration> listConfig = new ArrayList<>();
  private RecyclerView recyclerView;
  private UserInfoAdapter infoAdapter;

  private static final String USERNAME_LABEL = "Имя пользователя";
  private static final String STATUS_LABEL = "Статус";
  private static final String PHONE_NUMBER_LABEL = "Номер телефона";

  private LovelyProgressDialog waitingDialog;

  private FirebaseAuth mAuth;
  private User friendsAccount;
  private Context context;
  private Activity activity;

  private ValueEventListener userListener = new ValueEventListener() {
    @Override
    public void onDataChange(DataSnapshot dataSnapshot) {

      friendsAccount = dataSnapshot.getValue(User.class);

      if(friendsAccount == null) return;

      friendsAccount.name = friendsMap.containsKey(friendsAccount.phoneNumber) ? friendsMap.get(friendsAccount.phoneNumber) : friendsAccount.name;

      setupArrayListInfo(friendsAccount);
      if (infoAdapter != null) {
        infoAdapter.notifyDataSetChanged();
      }

      if (tvUserName != null) {
        tvUserName.setText(friendsAccount.name);
      }

      getSupportActionBar().setTitle(friendsAccount.name);

      setImageAvatar(context, friendsAccount.avata);
      SharedPreferenceHelper preferenceHelper = SharedPreferenceHelper.getInstance(context);
      preferenceHelper.saveUserInfo(friendsAccount);
    }

    @Override
    public void onCancelled(DatabaseError databaseError) {
      Log.e(FriendProfileActivity.class.getName(), "loadPost:onCancelled", databaseError.toException());
    }
  };

  public void setupArrayListInfo(User myAccount) {
    listConfig.clear();
    Configuration userNameConfig = new Configuration(USERNAME_LABEL, myAccount.name, R.mipmap.ic_account_box);
    listConfig.add(userNameConfig);

    Configuration statusConfig = new Configuration(STATUS_LABEL, myAccount.status.text, R.drawable.status);
    listConfig.add(statusConfig);

    Configuration phoneNumberConfig = new Configuration(PHONE_NUMBER_LABEL, myAccount.phoneNumber, R.mipmap.ic_email);
    listConfig.add(phoneNumberConfig);
  }

  private void setImageAvatar(Context context, String imgBase64) {
    try {
      Resources res = getResources();
      Bitmap src;
      if (imgBase64.equals("default")) {
        src = BitmapFactory.decodeResource(res, R.drawable.default_avata);
      } else {
        byte[] decodedString = Base64.decode(imgBase64, Base64.DEFAULT);
        src = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
      }

      avatar.setImageDrawable(ImageUtils.roundedImage(context, src));
    } catch (Exception e) {
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  public class UserInfoAdapter extends RecyclerView.Adapter<FriendProfileActivity.UserInfoAdapter.ViewHolder> {
    private List<Configuration> profileConfig;

    public UserInfoAdapter(List<Configuration> profileConfig) {
      this.profileConfig = profileConfig;
    }

    @Override
    public FriendProfileActivity.UserInfoAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      View itemView = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.list_info_item_layout, parent, false);
      return new FriendProfileActivity.UserInfoAdapter.ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(FriendProfileActivity.UserInfoAdapter.ViewHolder holder, int position) {
      final Configuration config = profileConfig.get(position);
      holder.label.setText(config.getLabel());
      holder.value.setText(config.getValue());
      holder.icon.setImageResource(config.getIcon());
    }

    @Override
    public int getItemCount() {
      return profileConfig.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
      public TextView label, value;
      public ImageView icon;

      public ViewHolder(View view) {
        super(view);
        label = (TextView) view.findViewById(R.id.tv_title);
        value = (TextView) view.findViewById(R.id.tv_detail);
        icon = (ImageView) view.findViewById(R.id.img_icon);
      }
    }

  }

}
