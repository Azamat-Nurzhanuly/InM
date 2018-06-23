package com.android.barracuda;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.android.barracuda.data.CallDB;
import com.android.barracuda.data.SharedPreferenceHelper;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.User;
import com.android.barracuda.service.ServiceUtils;
import com.android.barracuda.service.SinchService;
import com.android.barracuda.service.cloud.CloudFunctions;
import com.android.barracuda.ui.CallListFragment;
import com.android.barracuda.ui.FriendsFragment;
import com.android.barracuda.ui.GroupFragment;
import com.facebook.accountkit.AccessToken;
import com.facebook.accountkit.Account;
import com.facebook.accountkit.AccountKit;
import com.facebook.accountkit.AccountKitCallback;
import com.facebook.accountkit.AccountKitError;
import com.facebook.accountkit.AccountKitLoginResult;
import com.facebook.accountkit.ui.AccountKitActivity;
import com.facebook.accountkit.ui.AccountKitConfiguration;
import com.facebook.accountkit.ui.LoginType;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.yarolegovich.lovelydialog.LovelyCustomDialog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class MainActivity extends BarracudaActivity implements ServiceConnection {

  private ViewPager viewPager;
  private TabLayout tabLayout = null;
  public static String STR_FRIEND_FRAGMENT = "FRIEND";
  public static String STR_GROUP_FRAGMENT = "GROUP";
  public static String STR_CONTACTS_FRAGMENT = "CONTACTS";
  public static String STR_INFO_FRAGMENT = "INFO";
  public static String STR_INFO_CALL = "CALL";
  public static String STR_CONTACTS = "CONTACTS";

  private FloatingActionButton floatButton;
  private ViewPagerAdapter adapter;

  private FirebaseUser user;
  private static final String TAG = MainActivity.class.getSimpleName();
  private static int APP_REQUEST_CODE = 99;

  private FirebaseAuth mAuth;
  private FirebaseAuth.AuthStateListener mAuthListener;
  private CloudFunctions mCloudFunctions;

  //SINCH
  private SinchService.SinchServiceInterface mSinchServiceInterface;

  public void onLogoutClick(View view) {
    AccountKit.logOut();
    mAuth.signOut();
  }

  public void onColorClick(View view) {

    switch (view.getId()) {
      case R.id.colorDarkBlue: {
        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putString(SharedPreferenceHelper.SHARE_COLOR, COLOR_DARK_BLUE).commit();
        break;
      }
      case R.id.colorBlue: {
        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putString(SharedPreferenceHelper.SHARE_COLOR, COLOR_BLUE).commit();
        break;
      }
      case R.id.colorPurple: {
        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putString(SharedPreferenceHelper.SHARE_COLOR, COLOR_PURPLE).commit();
        break;
      }
      case R.id.colorOrange: {
        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putString(SharedPreferenceHelper.SHARE_COLOR, COLOR_ORANGE).commit();
        break;
      }
    }

    finish();
    Intent intent = new Intent(this, MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
  }

  public static int getThemeColor(Activity activity) {

    SharedPreferences sharedPreferences = activity.getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
    final String color = sharedPreferences.getString(SharedPreferenceHelper.SHARE_COLOR, "");
    int themePrimaryColor;

    switch (color) {
      case COLOR_DARK_BLUE: {
        themePrimaryColor = R.color.darkBlue;
        break;
      }
      case COLOR_BLUE: {
        themePrimaryColor = R.color.blue;
        break;
      }
      case COLOR_PURPLE: {
        themePrimaryColor = R.color.purple;
        break;
      }
      case COLOR_ORANGE: {
        themePrimaryColor = R.color.orange;
        break;
      }
      default: {
        themePrimaryColor = R.color.blue;
      }
    }

    return themePrimaryColor;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setTheme();

    //SINCH
    getApplicationContext().bindService(new Intent(this, SinchService.class), this,
      BIND_AUTO_CREATE);

    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);

    setContentView(R.layout.activity_main);

    getSupportActionBar().setTitle(R.string.app_name);

    viewPager = (ViewPager) findViewById(R.id.viewpager);
    floatButton = (FloatingActionButton) findViewById(R.id.fab);
    initTab();

    mAuth = FirebaseAuth.getInstance();

    mAuthListener = new FirebaseAuth.AuthStateListener() {
      @Override
      public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {

        user = firebaseAuth.getCurrentUser();
        if (user != null) {
          StaticConfig.UID = user.getUid();
          Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
          saveUserInfo();
        } else {

          Log.d(TAG, "onAuthStateChanged:signed_out");

          final AccessToken accessToken = AccountKit.getCurrentAccessToken();
          if (accessToken != null) {
            getCustomToken(accessToken);
          } else {
            phoneLogin();
          }
        }
      }


    };

    final Retrofit retrofit = new Retrofit.Builder()
      .baseUrl(BuildConfig.CLOUD_FUNCTIONS_BASE_URL)
      .build();
    mCloudFunctions = retrofit.create(CloudFunctions.class);

    //Create Table Calls
    CallDB.getInstance(this).createDB();
  }

  @Override
  protected void onStart() {
    super.onStart();
    mAuth.addAuthStateListener(mAuthListener);
    ServiceUtils.stopServiceFriendChat(getApplicationContext(), false);
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (mAuthListener != null) {
      mAuth.removeAuthStateListener(mAuthListener);
    }
  }

  @Override
  protected void onDestroy() {
    ServiceUtils.startServiceFriendChat(getApplicationContext());
    super.onDestroy();
  }

  void saveUserInfo() {
    FirebaseDatabase.getInstance().getReference().child("user/" + StaticConfig.UID).addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        HashMap hashUser = (HashMap) dataSnapshot.getValue();
        User userInfo = new User();
        assert hashUser != null;
        userInfo.name = (String) hashUser.get("name");
        userInfo.phoneNumber = (String) hashUser.get("phoneNumber");
        userInfo.avata = (String) hashUser.get("avata");
        SharedPreferenceHelper.getInstance(MainActivity.this).saveUserInfo(userInfo);
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {

      }
    });
  }

  void initNewUserInfo() {

    AccountKit.getCurrentAccount(new AccountKitCallback<Account>() {
      @Override
      public void onSuccess(final Account account) {

        User newUser = new User();
        newUser.id = account.getId();
        newUser.phoneNumber = account.getPhoneNumber().getPhoneNumber();
        newUser.name = account.getPhoneNumber().getPhoneNumber();
        newUser.avata = StaticConfig.STR_DEFAULT_BASE64;
        FirebaseDatabase.getInstance().getReference().child("user/" + user.getUid()).setValue(newUser);
      }

      @Override
      public void onError(final AccountKitError error) {
        Log.e(TAG, "GetCurrentAccountError: " + error);
      }
    });
  }

  /**
   * Khoi tao 3 tab
   */
  private void initTab() {
    tabLayout = (TabLayout) findViewById(R.id.tabs);
    tabLayout.setSelectedTabIndicatorColor(getResources().getColor(R.color.colorIndivateTab));
    setupViewPager(viewPager);
    tabLayout.setupWithViewPager(viewPager);
    setupTabIcons();
  }


  private void setupTabIcons() {
    int[] tabIcons = {
      R.drawable.ic_tab_person,
      R.drawable.ic_tab_group,
      R.drawable.ic_call,
      R.drawable.ic_contact
    };

    tabLayout.getTabAt(0).setIcon(tabIcons[0]);
    tabLayout.getTabAt(1).setIcon(tabIcons[1]);
    tabLayout.getTabAt(2).setIcon(tabIcons[2]);
    tabLayout.getTabAt(3).setIcon(tabIcons[3]);
  }

  private void setupViewPager(ViewPager viewPager) {
    adapter = new ViewPagerAdapter(getSupportFragmentManager());
    adapter.addFrag(new FriendsFragment(), STR_FRIEND_FRAGMENT);
    adapter.addFrag(new GroupFragment(), STR_GROUP_FRAGMENT);

    //TODO tabs
    adapter.addFrag(new CallListFragment(), STR_INFO_CALL);

    adapter.addFrag(new ContactsActivity.PlaceholderFragment(), STR_CONTACTS_FRAGMENT);

    floatButton.setOnClickListener(((FriendsFragment) adapter.getItem(0)).onClickFloatButton.getInstance(this));

    viewPager.setAdapter(adapter);
    viewPager.setOffscreenPageLimit(3);
    viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
      @Override
      public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

      }

      @Override
      public void onPageSelected(int position) {
        ServiceUtils.stopServiceFriendChat(MainActivity.this.getApplicationContext(), false);
        if (adapter.getItem(position) instanceof FriendsFragment) {
          floatButton.setVisibility(View.VISIBLE);
          floatButton.setOnClickListener(((FriendsFragment) adapter.getItem(position)).onClickFloatButton.getInstance(MainActivity.this));
          floatButton.setImageResource(R.drawable.plus);
        } else if (adapter.getItem(position) instanceof GroupFragment) {
          floatButton.setVisibility(View.VISIBLE);
          floatButton.setOnClickListener(((GroupFragment) adapter.getItem(position)).onClickFloatButton.getInstance(MainActivity.this));
          floatButton.setImageResource(R.drawable.ic_float_add_group);
        } else {
          floatButton.setVisibility(View.GONE);
        }
      }

      @Override
      public void onPageScrollStateChanged(int state) {

      }
    });
  }

  @Override
  protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == APP_REQUEST_CODE) {
      handleFacebookLoginResult(resultCode, data);
    }
  }

  private void handleFacebookLoginResult(final int resultCode, final Intent data) {
    final AccountKitLoginResult loginResult =
      data.getParcelableExtra(AccountKitLoginResult.RESULT_KEY);

    if (loginResult.getError() != null) {
      final String toastMessage = loginResult.getError().getErrorType().getMessage();
      Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
    } else if (loginResult.wasCancelled() || resultCode == RESULT_CANCELED) {
      Log.d(TAG, "Login cancelled");
      finish();
    } else {
      if (loginResult.getAccessToken() != null) {
        Log.d(TAG, "We have logged with FB Account Kit. ID: " +
          loginResult.getAccessToken().getAccountId());
        getCustomToken(loginResult.getAccessToken());
      } else {
        Log.wtf(TAG, "It should not have been happened");
      }
    }
  }

  private void getCustomToken(final AccessToken accessToken) {
    Log.d(TAG, "Getting custom token for Account Kit access token: " + accessToken.getToken());
    mCloudFunctions.getCustomToken(accessToken.getToken()).enqueue(new Callback<ResponseBody>() {
      @Override
      public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
        try {
          if (response.isSuccessful()) {
            final String customToken = response.body().string();
            Log.d(TAG, "Custom token: " + customToken);
            signInWithCustomToken(customToken);
          } else {
            Log.e(TAG, response.errorBody().string());
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

      @Override
      public void onFailure(Call<ResponseBody> call, Throwable e) {
        Log.e(TAG, "Request getCustomToken failed", e);
      }
    });
  }

  private void signInWithCustomToken(String customToken) {
    mAuth.signInWithCustomToken(customToken)
      .addOnCompleteListener(MainActivity.this, new OnCompleteListener<AuthResult>() {
        @Override
        public void onComplete(@NonNull Task<AuthResult> task) {
          Log.d(TAG, "getCustomToken:onComplete:" + task.isSuccessful());
          if (!task.isSuccessful()) {
            Log.w(TAG, "getCustomToken", task.getException());
            Toast.makeText(MainActivity.this, "Authentication failed.",
              Toast.LENGTH_SHORT).show();
          } else {
            initNewUserInfo();
          }
        }
      });
  }

  private void phoneLogin() {
    final Intent intent = new Intent(this, AccountKitActivity.class);
    final AccountKitConfiguration.AccountKitConfigurationBuilder configurationBuilder =
      new AccountKitConfiguration.AccountKitConfigurationBuilder(LoginType.PHONE,
        AccountKitActivity.ResponseType.TOKEN);
    intent.putExtra(AccountKitActivity.ACCOUNT_KIT_ACTIVITY_CONFIGURATION,
      configurationBuilder.build());
    startActivityForResult(intent, APP_REQUEST_CODE);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {

    SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
    final Boolean incognito = sharedPreferences.getBoolean(SharedPreferenceHelper.INCOGNITO, false);

    MenuItem item = menu.findItem(R.id.incognito);

    if (item != null) {

      item.setChecked(incognito);
    }

    return true;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    switch (item.getItemId()) {
      case R.id.setProfile: {

        Intent profIntent = new Intent(this, ProfileActivity.class);
        startActivity(profIntent);
        break;
      }
      case R.id.chats_themes: {

        new LovelyCustomDialog(this)
          .setView(R.layout.color_selector)
          .show();
      }
      case R.id.incognito: {

        if (item.isChecked()) {
          item.setChecked(false);
        } else {
          item.setChecked(true);
        }

        SharedPreferences sharedPreferences = getSharedPreferences(SharedPreferenceHelper.USER_SELECTION, MODE_PRIVATE);
        sharedPreferences.edit().putBoolean(SharedPreferenceHelper.INCOGNITO, item.isChecked()).commit();
      }
      case R.id.favMes: {
        Intent intent = new Intent(this, FavoritesActivity.class);
        startActivity(intent);
        break;
      }
      case R.id.account_rules: {
        copyReadAssets("Правила конфиденциальности.pdf");
        break;
      }
      case R.id.account_contract: {
        copyReadAssets("Лицензионный договор.pdf");
        break;
      }
      case R.id.account_blacklist: {
        Intent intent = new Intent(this, BlacklistActivity.class);
        startActivity(intent);
        break;
      }
      default:
    }

    return super.onOptionsItemSelected(item);
  }

  private void copyReadAssets(String name) {
    AssetManager assetManager = getAssets();

    InputStream in = null;
    OutputStream out = null;
    File file = new File(getFilesDir(), name);
    try {
      in = assetManager.open(name);
      out = openFileOutput(file.getName(), Context.MODE_WORLD_READABLE);

      copyFile(in, out);
      in.close();
      in = null;
      out.flush();
      out.close();
      out = null;
    } catch (Exception e) {
      Log.e("tag", e.getMessage());
    }

    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setDataAndType(
      Uri.parse("file://" + getFilesDir() + "/" + name),
      "application/pdf");

    startActivity(intent);
  }

  private void copyFile(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[1024];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
  }

  class ViewPagerAdapter extends FragmentPagerAdapter {
    private final List<Fragment> mFragmentList = new ArrayList<>();
    private final List<String> mFragmentTitleList = new ArrayList<>();

    public ViewPagerAdapter(FragmentManager manager) {
      super(manager);
    }

    @Override
    public Fragment getItem(int position) {
      return mFragmentList.get(position);
    }

    @Override
    public int getCount() {
      return mFragmentList.size();
    }

    public void addFrag(Fragment fragment, String title) {
      mFragmentList.add(fragment);
      mFragmentTitleList.add(title);
    }

    @Override
    public CharSequence getPageTitle(int position) {

      // return null to display only the icon
      return null;
    }
  }

  //FOR SINCH
  @Override
  public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
    if (SinchService.class.getName().equals(componentName.getClassName())) {
      mSinchServiceInterface = (SinchService.SinchServiceInterface) iBinder;
      onServiceConnected();
    }
  }

  @Override
  public void onServiceDisconnected(ComponentName componentName) {
    if (SinchService.class.getName().equals(componentName.getClassName())) {
      mSinchServiceInterface = null;
      onServiceDisconnected();
    }
  }

  protected void onServiceConnected() {

    if (user != null) {
      if (!user.getUid().equals(getSinchServiceInterface().getUserName())) {
        getSinchServiceInterface().stopClient();
      }
      if (!getSinchServiceInterface().isStarted()) {
        getSinchServiceInterface().startClient(user.getUid());
      }
    }
  }

  protected void onServiceDisconnected() {
    // for subclasses
  }

  protected SinchService.SinchServiceInterface getSinchServiceInterface() {
    return mSinchServiceInterface;
  }
}