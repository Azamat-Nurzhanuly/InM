package com.android.barracuda.ui;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.android.barracuda.BuildConfig;
import com.android.barracuda.service.cloud.CloudFunctions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.android.barracuda.MainActivity;
import com.android.barracuda.R;
import com.android.barracuda.data.SharedPreferenceHelper;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.User;
import com.sinch.verification.CodeInterceptionException;
import com.sinch.verification.Config;
import com.sinch.verification.IncorrectCodeException;
import com.sinch.verification.InitiationResult;
import com.sinch.verification.InvalidInputException;
import com.sinch.verification.ServiceErrorException;
import com.sinch.verification.SinchVerification;
import com.sinch.verification.Verification;
import com.sinch.verification.VerificationListener;
import com.yarolegovich.lovelydialog.LovelyInfoDialog;
import com.yarolegovich.lovelydialog.LovelyProgressDialog;

import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Pattern;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;


public class LoginActivity extends AppCompatActivity {
  private static String TAG = "LoginActivity";
  FloatingActionButton fab;
  private final Pattern VALID_EMAIL_ADDRESS_REGEX =
    Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);
  private EditText editTextUsername, editTextPassword;
  private LovelyProgressDialog waitingDialog;

  private AuthUtils authUtils;
  private FirebaseAuth mAuth;
  private FirebaseAuth.AuthStateListener mAuthListener;
  private FirebaseUser user;
  private boolean firstTimeAccess;
  private CloudFunctions mCloudFunctions;
  private int PERMISSION_REQUEST_CODE = 200;
  private String phoneNumberSt = "";

  @Override
  protected void onStart() {
    super.onStart();
    mAuth.addAuthStateListener(mAuthListener);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_login);
    fab = (FloatingActionButton) findViewById(R.id.fab);
    editTextUsername = (EditText) findViewById(R.id.et_username);
    editTextPassword = (EditText) findViewById(R.id.et_password);
    firstTimeAccess = true;

    final Retrofit retrofit = new Retrofit.Builder()
      .baseUrl(BuildConfig.CLOUD_FUNCTIONS_BASE_URL)
      .build();
    mCloudFunctions = retrofit.create(CloudFunctions.class);

    requestMultiplePermissions();

    initFirebase();
  }

  public void requestMultiplePermissions() {
    ActivityCompat.requestPermissions(this,
      new String[]{
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE
      },
      PERMISSION_REQUEST_CODE);
  }

  private void initFirebase() {

    mAuth = FirebaseAuth.getInstance();
    authUtils = new AuthUtils();
    mAuthListener = new FirebaseAuth.AuthStateListener() {
      @Override
      public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
        user = firebaseAuth.getCurrentUser();
        if (user != null) {

          StaticConfig.UID = user.getUid();
          Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
          if (firstTimeAccess) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            LoginActivity.this.finish();
          }
        } else {
          Log.d(TAG, "onAuthStateChanged:signed_out");
        }
        firstTimeAccess = false;
      }
    };


    waitingDialog = new LovelyProgressDialog(this).setCancelable(false);
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (mAuthListener != null) {
      mAuth.removeAuthStateListener(mAuthListener);
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == StaticConfig.REQUEST_CODE_REGISTER && resultCode == RESULT_OK) {
      authUtils.createUser(data.getStringExtra(StaticConfig.STR_EXTRA_USERNAME), data.getStringExtra(StaticConfig.STR_EXTRA_PASSWORD));
    }
  }

  public void clickLogin(View view) {

    String phoneNumber = editTextUsername.getText().toString();

    if (validatePhoneNumber(phoneNumber)) {
      ((Button) view).setEnabled(false);
      authUtils.flashCallVerification(phoneNumber);
    } else {
      new LovelyInfoDialog(LoginActivity.this) {
        @Override
        public LovelyInfoDialog setConfirmButtonText(String text) {
          findView(com.yarolegovich.lovelydialog.R.id.ld_btn_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              dismiss();
            }
          });
          return super.setConfirmButtonText(text);
        }
      }
        .setTopColorRes(R.color.colorDarkBluePrimary)
        .setIcon(R.drawable.ic_person_low)
        .setTitle("Неуспешная авторизация")
        .setMessage("Неверно введён телефонный номер, телефон вводится без 8 и не должен начинаться с +7")
        .setCancelable(false)
        .setConfirmButtonText("Ok")
        .show();
    }
  }

  @Override
  public void onBackPressed() {
    super.onBackPressed();
    setResult(RESULT_CANCELED, null);
    finish();
  }

  private boolean validatePhoneNumber(String phoneNumber) {

    return (phoneNumber.length() == 10 && phoneNumber.startsWith("7"))
      || (phoneNumber.length() == 11 && phoneNumber.startsWith("8"))
      || (phoneNumber.length() == 12 && phoneNumber.startsWith("+7"));
  }

  class AuthUtils {

    private Verification verification;

    /**
     * Action register
     *
     * @param email
     * @param password
     */
    void createUser(String email, String password) {
      waitingDialog.setIcon(R.drawable.ic_add_friend)
        .setTitle("Registering....")
        .setTopColorRes(R.color.colorDarkBluePrimary)
        .show();
      mAuth.createUserWithEmailAndPassword(email, password)
        .addOnCompleteListener(LoginActivity.this, new OnCompleteListener<AuthResult>() {
          @Override
          public void onComplete(@NonNull Task<AuthResult> task) {
            Log.d(TAG, "createUserWithEmail:onComplete:" + task.isSuccessful());
            waitingDialog.dismiss();
            // If sign in fails, display a message to the user. If sign in succeeds
            // the auth state listener will be notified and logic to handle the
            // signed in user can be handled in the listener.
            if (!task.isSuccessful()) {
              new LovelyInfoDialog(LoginActivity.this) {
                @Override
                public LovelyInfoDialog setConfirmButtonText(String text) {
                  findView(com.yarolegovich.lovelydialog.R.id.ld_btn_confirm).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                      dismiss();
                    }
                  });
                  return super.setConfirmButtonText(text);
                }
              }
                .setTopColorRes(R.color.colorAccent)
                .setIcon(R.drawable.ic_add_friend)
                .setTitle("Register false")
                .setMessage("Email exist or weak password!")
                .setConfirmButtonText("ok")
                .setCancelable(false)
                .show();
            } else {
              initNewUserInfo();
              Toast.makeText(LoginActivity.this, "Register and Login success", Toast.LENGTH_SHORT).show();
              startActivity(new Intent(LoginActivity.this, MainActivity.class));
              LoginActivity.this.finish();
            }
          }
        })
        .addOnFailureListener(new OnFailureListener() {
          @Override
          public void onFailure(@NonNull Exception e) {
            waitingDialog.dismiss();
          }
        });
    }

    void flashCallVerification(final String phoneNumber) {

      waitingDialog.setIcon(R.drawable.ic_person_low)
        .setTitle("Авторизация....")
        .setTopColorRes(R.color.colorDarkBluePrimary)
        .show();

      Config config = SinchVerification.config().applicationKey(StaticConfig.SINCH_KEY).context(getApplicationContext()).build();

      VerificationListener listener = new VerificationListener() {

        @Override
        public void onInitiated(InitiationResult initiationResult) {
        }

        @Override
        public void onInitiationFailed(Exception e) {

          waitingDialog.dismiss();

          String description = "Возникла ошибка при афторизации, проверьте введённый номер телефона";

          if (e instanceof InvalidInputException) {
            // Incorrect number provided
            Log.w(TAG, "Incorrect number or code provided");
            description = "Неправильно введён номер телефона";
          } else if (e instanceof ServiceErrorException) {
            // Sinch service error
            Log.w(TAG, "Sinch service error");
          } else {
            // Other system error, such as UnknownHostException in case of network error
            Log.w(TAG, "Other system error, such as UnknownHostException in case of network error");
          }

          new LovelyInfoDialog(LoginActivity.this) {
            @Override
            public LovelyInfoDialog setConfirmButtonText(String text) {
              findView(com.yarolegovich.lovelydialog.R.id.ld_btn_confirm).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                  dismiss();
                }
              });
              return super.setConfirmButtonText(text);
            }
          }
            .setTopColorRes(R.color.colorDarkBluePrimary)
            .setIcon(R.drawable.ic_person_low)
            .setTitle("Неуспешная авторизация")
            .setMessage(description)
            .setCancelable(false)
            .setConfirmButtonText("Ok")
            .show();
        }

        @Override
        public void onVerified() {
          waitingDialog.dismiss();
          getCustomToken(phoneNumber);
        }

        @Override
        public void onVerificationFailed(Exception e) {

          waitingDialog.dismiss();
          String description = "Возникла ошибка при афторизации, проверьте введённый номер телефона";

          if (e instanceof CodeInterceptionException) {
            // Intercepting the verification code automatically failed, input the code manually with verify()
            Log.w(TAG, "Intercepting the verification code automatically failed, input the code manually with verify()");
          } else if (e instanceof ServiceErrorException) {
            // Sinch service error
            Log.w(TAG, "Sinch service error");
          } else {
            // Other system error, such as UnknownHostException in case of network error
            Log.w(TAG, "Other system error, such as UnknownHostException in case of network error");
          }

          new LovelyInfoDialog(LoginActivity.this) {
            @Override
            public LovelyInfoDialog setConfirmButtonText(String text) {
              findView(com.yarolegovich.lovelydialog.R.id.ld_btn_confirm).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                  dismiss();
                }
              });
              return super.setConfirmButtonText(text);
            }
          }
            .setTopColorRes(R.color.colorDarkBluePrimary)
            .setIcon(R.drawable.ic_person_low)
            .setTitle("Неуспешная авторизация")
            .setMessage(description)
            .setCancelable(false)
            .setConfirmButtonText("Ok")
            .show();
        }

        @Override
        public void onVerificationFallback() {

        }
      };

      verification = SinchVerification.createFlashCallVerification(config, "+7" + phoneNumber, listener);
      verification.initiate();
    }

    private void getCustomToken(String phoneNumber) {

      phoneNumberSt = phoneNumber;

      mCloudFunctions.getCustomToken(phoneNumber).enqueue(new Callback<ResponseBody>() {
        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
          try {
            if (response.isSuccessful()) {
              final String customToken = response.body().string();
              Log.d(TAG, "Custom token: " + customToken);
              signIn(customToken);
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

    void signIn(String customToken) {
      waitingDialog.setIcon(R.drawable.ic_person_low)
        .setTitle("Выполняется вход....")
        .setTopColorRes(R.color.colorDarkBluePrimary)
        .show();
      mAuth.signInWithCustomToken(customToken)
        .addOnCompleteListener(LoginActivity.this, new OnCompleteListener<AuthResult>() {
          @Override
          public void onComplete(@NonNull Task<AuthResult> task) {
            Log.d(TAG, "signInWithCustomToken:onComplete:" + task.isSuccessful());
            // If sign in fails, display a message to the user. If sign in succeeds
            // the auth state listener will be notified and logic to handle the
            // signed in user can be handled in the listener.
            waitingDialog.dismiss();
            if (!task.isSuccessful()) {
              Log.w(TAG, "signInWithEmail:failed", task.getException());
              new LovelyInfoDialog(LoginActivity.this) {
                @Override
                public LovelyInfoDialog setConfirmButtonText(String text) {
                  findView(com.yarolegovich.lovelydialog.R.id.ld_btn_confirm).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                      dismiss();
                    }
                  });
                  return super.setConfirmButtonText(text);
                }
              }
                .setTopColorRes(R.color.colorDarkBluePrimary)
                .setIcon(R.drawable.ic_person_low)
                .setTitle("Login false")
                .setMessage("Email not exist or wrong password!")
                .setCancelable(false)
                .setConfirmButtonText("Ok")
                .show();
            } else {
              saveUserInfo();
              startActivity(new Intent(LoginActivity.this, MainActivity.class));
              LoginActivity.this.finish();
            }
          }
        })
        .addOnFailureListener(new OnFailureListener() {
          @Override
          public void onFailure(@NonNull Exception e) {
            waitingDialog.dismiss();
          }
        });
    }

    void saveUserInfo() {
      FirebaseDatabase.getInstance().getReference().child("user/" + StaticConfig.UID).addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
          waitingDialog.dismiss();
          HashMap hashUser = (HashMap) dataSnapshot.getValue();

          if (hashUser == null) {

            User user = initNewUserInfo();
            SharedPreferenceHelper.getInstance(LoginActivity.this).saveUserInfo(user);
          } else {

            User userInfo = new User();
            userInfo.name = (String) hashUser.get("name");
            userInfo.phoneNumber = (String) hashUser.get("phoneNumber");
            userInfo.avata = (String) hashUser.get("avata");
            SharedPreferenceHelper.getInstance(LoginActivity.this).saveUserInfo(userInfo);
          }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
      });
    }

    User initNewUserInfo() {
      User newUser = new User();
      newUser.phoneNumber = phoneNumberSt;
      newUser.name = phoneNumberSt;
      newUser.status.text = "Hey, there! I'm using Barracuda!";
      newUser.avata = StaticConfig.STR_DEFAULT_BASE64;
      FirebaseDatabase.getInstance().getReference().child("user/" + user.getUid()).setValue(newUser);

      return newUser;
    }
  }
}
