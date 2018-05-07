package com.android.barracuda.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.barracuda.BuildConfig;
import com.android.barracuda.R;
import com.android.barracuda.data.SharedPreferenceHelper;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.fab.Fab;
import com.android.barracuda.model.Consersation;
import com.android.barracuda.model.FileModel;
import com.android.barracuda.model.Message;
import com.bumptech.glide.Glide;
import com.devlomi.record_view.OnBasketAnimationEnd;
import com.devlomi.record_view.OnRecordClickListener;
import com.devlomi.record_view.OnRecordListener;
import com.devlomi.record_view.RecordButton;
import com.devlomi.record_view.RecordView;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.gordonwong.materialsheetfab.MaterialSheetFab;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import de.hdodenhof.circleimageview.CircleImageView;


public class ChatActivity extends AppCompatActivity implements View.OnClickListener {
  private RecyclerView recyclerChat;
  public static final int VIEW_TYPE_USER_MESSAGE_TEXT = 0;
  public static final int VIEW_TYPE_FRIEND_MESSAGE_TEXT = 1;
  public static final int VIEW_TYPE_USER_MESSAGE_FILE = 2;
  public static final int VIEW_TYPE_FRIEND_MESSAGE_FILE = 3;

  static final String TAG = ChatActivity.class.getSimpleName();

  public static final String MESSAGE_TYPE_TEXT = "text";
  public static final String MESSAGE_TYPE_IMAGE = "img";

  private static final int IMAGE_GALLERY_REQUEST = 1;
  private static final int IMAGE_CAMERA_REQUEST = 2;
  private static final int PLACE_PICKER_REQUEST = 3;
  private static final int CONTACT_PICKER_REQUEST = 4;
  private static final int AUDIO_PICKER_REQUEST = 5;
  private static final int DOC_PICKER_REQUEST = 6;

  private File filePathImageCamera;

  private ListMessageAdapter adapter;
  private String roomId;
  private ArrayList<CharSequence> idFriend;
  private Consersation consersation;

  private ImageButton btnSend;
  private ImageButton btn_choose_doc;
  private ImageButton btn_choose_camera;
  private ImageButton btn_choose_gallery;
  private ImageButton btn_choose_audio;
  private ImageButton btn_choose_place;
  private ImageButton btn_choose_contact;

  private RecordButton recordButton;


  private EditText editWriteMessage;
  private LinearLayoutManager linearLayoutManager;
  public static HashMap<String, Bitmap> bitmapAvataFriend;

  public Bitmap bitmapAvataUser;
  public MaterialSheetFab materialSheetFab;
  public Fab fab = null;

  FirebaseStorage storage = FirebaseStorage.getInstance();

  @RequiresApi(api = Build.VERSION_CODES.M)
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_chat);
    Intent intentData = getIntent();
    idFriend = intentData.getCharSequenceArrayListExtra(StaticConfig.INTENT_KEY_CHAT_ID);
    roomId = intentData.getStringExtra(StaticConfig.INTENT_KEY_CHAT_ROOM_ID);
    String nameFriend = intentData.getStringExtra(StaticConfig.INTENT_KEY_CHAT_FRIEND);

    consersation = new Consersation();


//    btnSend = (ImageButton) findViewById(R.id.btnSend);
    btn_choose_doc = (ImageButton) findViewById(R.id.btn_choose_doc);
    btn_choose_camera = (ImageButton) findViewById(R.id.btn_choose_camera);
    btn_choose_gallery = (ImageButton) findViewById(R.id.btn_choose_gallery);
    btn_choose_audio = (ImageButton) findViewById(R.id.btn_choose_audio);
    btn_choose_place = (ImageButton) findViewById(R.id.btn_choose_place);
    btn_choose_contact = (ImageButton) findViewById(R.id.btn_choose_contact);

//    btnSend.setOnClickListener(this);
    btn_choose_doc.setOnClickListener(this);
    btn_choose_camera.setOnClickListener(this);
    btn_choose_gallery.setOnClickListener(this);
    btn_choose_audio.setOnClickListener(this);
    btn_choose_place.setOnClickListener(this);
    btn_choose_contact.setOnClickListener(this);


    String base64AvataUser = SharedPreferenceHelper.getInstance(this).getUserInfo().avata;
    if (!base64AvataUser.equals(StaticConfig.STR_DEFAULT_BASE64)) {
      byte[] decodedString = Base64.decode(base64AvataUser, Base64.DEFAULT);
      bitmapAvataUser = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
    } else {
      bitmapAvataUser = null;
    }

    initAudioButtons();

    editWriteMessage = (EditText) findViewById(R.id.editWriteMessage);
    initEditText(nameFriend);


//    fab = (Fab) findViewById(R.id.fab);
//    final View sheetView = findViewById(R.id.fab_sheet);
//    sheetView.setFocusable(true);
//
//    View overlay = findViewById(R.id.overlay);
//    int sheetColor = getResources().getColor(R.color.fab_sheet_color);
//    int fabColor = getResources().getColor(R.color.fab_color);

    // Initialize material sheet FAB
//    materialSheetFab = new MaterialSheetFab<>(fab, sheetView, overlay,
//      sheetColor, fabColor);
//
//    fab.setOnClickListener(new View.OnClickListener() {
//      @Override
//      public void onClick(View v) {
//        materialSheetFab.showSheet();
//      }
//    });
//
//    sheetView.setFocusable(true);
//
//    sheetView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
//      @Override
//      public void onFocusChange(View v, boolean hasFocus) {
//        if (!hasFocus) {
//          materialSheetFab.hideSheet();
//        }
//      }
//    });


  }

  private void initAudioButtons() {
    //audio record
    RecordView recordView = (RecordView) findViewById(R.id.record_view);
    recordButton = (RecordButton) findViewById(R.id.record_button);

    //IMPORTANT
    recordButton.setRecordView(recordView);

    recordView.setCancelBounds(130);

    recordButton.setOnRecordClickListener(new OnRecordClickListener() {
      @Override
      public void onClick(View v) {
//        Toast.makeText(ChatActivity.this, "RECORD BUTTON CLICKED", Toast.LENGTH_SHORT).show();
        sendTextMessage();
        Log.d("RecordButton", "RECORD BUTTON CLICKED");
      }
    });

    recordView.setOnBasketAnimationEndListener(new OnBasketAnimationEnd() {
      @Override
      public void onAnimationEnd() {
        Log.d("RecordView", "Basket Animation Finished");
      }
    });


    recordView.setOnRecordListener(new OnRecordListener() {
      @Override
      public void onStart() {
        //Start Recording..
        Log.d("RecordView", "onStart");
      }

      @Override
      public void onCancel() {
        //On Swipe To Cancel
        Log.d("RecordView", "onCancel");

      }

      @Override
      public void onFinish(long recordTime) {
        //Stop Recording..
        System.out.println(recordTime);
        Log.d("RecordView", "onFinish");

        Log.d("RecordTime", String.valueOf(recordTime));
      }

      @Override
      public void onLessThanSecond() {
        //When the record time is less than One Second
        Log.d("RecordView", "onLessThanSecond");
      }
    });
  }

  private void initEditText(String nameFriend) {
    if (idFriend != null && nameFriend != null) {
      getSupportActionBar().setTitle(nameFriend);
      linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
      recyclerChat = (RecyclerView) findViewById(R.id.recyclerChat);
      recyclerChat.setLayoutManager(linearLayoutManager);
      adapter = new ListMessageAdapter(this, consersation, bitmapAvataFriend, bitmapAvataUser);
      FirebaseDatabase.getInstance().getReference().child("message/" + roomId).addChildEventListener(new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
          if (dataSnapshot.getValue() != null) {
            HashMap mapMessage = (HashMap) dataSnapshot.getValue();

            Message newMessage = new Message();
            newMessage.idSender = (String) mapMessage.get("idSender");
            newMessage.idReceiver = (String) mapMessage.get("idReceiver");
            newMessage.timestamp = (long) mapMessage.get("timestamp");

            if (mapMessage.get("text") != null) {
              newMessage.text = (String) mapMessage.get("text");
            }


            if (mapMessage.get("fileModel") != null) {
              FileModel fileModel = new FileModel();
              HashMap fileHash = (HashMap) mapMessage.get("fileModel");
              if (fileHash.containsKey("type"))
                fileModel.type = (String) fileHash.get("type");

              if (fileHash.containsKey("name_file"))
                fileModel.name_file = (String) fileHash.get("name_file");

              if (fileHash.containsKey("url_file"))
                fileModel.url_file = (String) fileHash.get("url_file");

              newMessage.fileModel = fileModel;
            }

            consersation.getListMessageData().add(newMessage);
            adapter.notifyDataSetChanged();
            linearLayoutManager.scrollToPosition(consersation.getListMessageData().size() - 1);
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
      recyclerChat.setAdapter(adapter);
    }

    editWriteMessage.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {

      }

      @Override
      public void afterTextChanged(Editable s) {
        if (editWriteMessage != null && editWriteMessage.getText().toString().trim().length() == 0) {
          recordButton.setListenForRecord(true);
          recordButton.setImageResource(R.drawable.record);
        } else {
          recordButton.setListenForRecord(false);
          recordButton.setImageResource(R.drawable.send);
        }
      }
    });
  }


  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {

    if (requestCode == IMAGE_GALLERY_REQUEST) {
      if (resultCode == RESULT_OK) {
        Uri selectedImageUri = data.getData();
        if (selectedImageUri != null) {

          sendImageFirebase(selectedImageUri);

        }
      }
    }
  }


//  private void sendFileFirebase(DatabaseReference databaseReference, final Uri file){
//    if (databaseReference != null){
//      final String name = DateFormat.format("yyyy-MM-dd_hhmmss", new Date()).toString();
//      DatabaseReference imageGalleryRef = databaseReference.child(name+"_gallery");
//      UploadTask uploadTask = imageGalleryRef.putFile(file);
//      uploadTask.addOnFailureListener(new OnFailureListener() {
//        @Override
//        public void onFailure(@NonNull Exception e) {
//          Log.e(TAG,"onFailure sendFileFirebase "+e.getMessage());
//        }
//      }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
//        @Override
//        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
//          Log.i(TAG,"onSuccess sendFileFirebase");
//          Uri downloadUrl = taskSnapshot.getDownloadUrl();
//          FileModel fileModel = new FileModel("img",downloadUrl.toString(),name,"");
//          ChatModel chatModel = new ChatModel(userModel,"", Calendar.getInstance().getTime().getTime()+"",fileModel);
//          mFirebaseDatabaseReference.child(CHAT_REFERENCE).push().setValue(chatModel);
//        }
//      });
//    }else{
//      //IS NULL
//    }
//
//  }


  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      Intent result = new Intent();
      result.putExtra("idFriend", idFriend.get(0));
      setResult(RESULT_OK, result);
      this.finish();
    }
    return true;
  }

  @Override
  public void onBackPressed() {
//    if (materialSheetFab.isSheetVisible()) {
//      materialSheetFab.hideSheet();
//      return;
//    }

    Intent result = new Intent();
    result.putExtra("idFriend", idFriend.get(0));
    setResult(RESULT_OK, result);
    this.finish();
  }

  @Override
  public void onClick(View view) {

    switch (view.getId()) {
//      case R.id.btnSend:
//        sendTextMessage();
//        break;
      case R.id.btn_choose_audio:
        chooseMedia(AUDIO_PICKER_REQUEST);
        break;
      case R.id.btn_choose_camera:
        chooseMedia(IMAGE_CAMERA_REQUEST);
        break;
      case R.id.btn_choose_contact:
        chooseMedia(CONTACT_PICKER_REQUEST);
        break;
      case R.id.btn_choose_doc:
        chooseMedia(DOC_PICKER_REQUEST);
        break;
      case R.id.btn_choose_gallery:
        chooseMedia(IMAGE_GALLERY_REQUEST);
        break;
      case R.id.btn_choose_place:
        chooseMedia(PLACE_PICKER_REQUEST);
        break;
    }
  }


  private void sendTextMessage() {
    String content = editWriteMessage.getText().toString().trim();
    if (content.length() > 0) {
      editWriteMessage.setText("");
      Message newMessage = new Message();
      newMessage.text = content;
      newMessage.fileModel = null;
      newMessage.idSender = StaticConfig.UID;
      newMessage.idReceiver = roomId;
      newMessage.timestamp = System.currentTimeMillis();
      FirebaseDatabase.getInstance().getReference().child("message/" + roomId).push().setValue(newMessage);
    }
  }

  private void sendImageFirebase(Uri selectedImageUri) {

    final Message newMessage = new Message();
    newMessage.text = null;
    final String nameOfImage = MESSAGE_TYPE_IMAGE + DateFormat.format("yyyy-MM-dd_hhmmss", new Date()).toString();

    StorageReference imageRef = storage.getReference().child("images/" + selectedImageUri.getLastPathSegment());

    UploadTask uploadTask = imageRef.putFile(selectedImageUri);
    uploadTask.addOnFailureListener(new OnFailureListener() {
      @Override
      public void onFailure(@NonNull Exception e) {
        Log.e(TAG, "onFailure sendFileFirebase " + e.getMessage());
      }
    }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
      @Override
      public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
        FileModel fileModel = new FileModel();
        fileModel.name_file = nameOfImage;
        fileModel.type = MESSAGE_TYPE_IMAGE;
        fileModel.url_file = taskSnapshot.getDownloadUrl().toString();

        newMessage.fileModel = fileModel;

        newMessage.idSender = StaticConfig.UID;
        newMessage.idReceiver = roomId;
        newMessage.timestamp = System.currentTimeMillis();
        FirebaseDatabase.getInstance().getReference().child("message/" + roomId).push().setValue(newMessage);
      }
    });

  }

  private void chooseMedia(int content) {
    if (content == AUDIO_PICKER_REQUEST) {
      Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.setType("audio/*");
      startActivityForResult(intent, AUDIO_PICKER_REQUEST);
      return;
    }
    if (content == PLACE_PICKER_REQUEST) {

      try {
        PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
        startActivityForResult(builder.build(this), PLACE_PICKER_REQUEST);
        return;
      } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
        e.printStackTrace();
      }
    }

    if (content == IMAGE_CAMERA_REQUEST) {
      String nomeFoto = DateFormat.format("yyyy-MM-dd_hhmmss", new Date()).toString();
      filePathImageCamera = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), nomeFoto + "/camera.jpg");
      Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
      Uri photoURI = FileProvider.getUriForFile(ChatActivity.this,
        BuildConfig.APPLICATION_ID + ".provider",
        filePathImageCamera);
      it.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
      startActivityForResult(it, IMAGE_CAMERA_REQUEST);
      return;
    }

    if (content == IMAGE_GALLERY_REQUEST) {
      Intent intent = new Intent();
      intent.setType("image/*");
      intent.setAction(Intent.ACTION_GET_CONTENT);
      startActivityForResult(Intent.createChooser(intent, ""), IMAGE_GALLERY_REQUEST);
      return;
    }
    if (content == DOC_PICKER_REQUEST) {
      Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.setType("file/*");
      startActivityForResult(intent, DOC_PICKER_REQUEST);

      return;
    }
    if (content == CONTACT_PICKER_REQUEST) {
      Intent pickIntent = new Intent(Intent.ACTION_PICK,
        android.provider.ContactsContract.Contacts.CONTENT_URI);
      startActivityForResult(pickIntent, CONTACT_PICKER_REQUEST);
      return;
    }

  }
}

class ListMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private Context context;
  private Consersation consersation;
  private HashMap<String, Bitmap> bitmapAvata;
  private HashMap<String, DatabaseReference> bitmapAvataDB;
  private Bitmap bitmapAvataUser;

  public ListMessageAdapter(Context context, Consersation consersation, HashMap<String, Bitmap> bitmapAvata, Bitmap bitmapAvataUser) {
    this.context = context;
    this.consersation = consersation;
    this.bitmapAvata = bitmapAvata;
    this.bitmapAvataUser = bitmapAvataUser;
    bitmapAvataDB = new HashMap<>();
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    if (viewType == ChatActivity.VIEW_TYPE_FRIEND_MESSAGE_TEXT) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_friend, parent, false);
      return new ItemMessageFriendHolder(view);
    } else if (viewType == ChatActivity.VIEW_TYPE_USER_MESSAGE_TEXT) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_user, parent, false);
      return new ItemMessageUserHolder(view);
    } else if (viewType == ChatActivity.VIEW_TYPE_USER_MESSAGE_FILE) {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_user_img, parent, false);
      return new ItemMessageUserHolder(view);
    } else {
      View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_friend_img, parent, false);
      return new ItemMessageFriendHolder(view);
    }
  }

  @Override
  public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
    if (holder instanceof ItemMessageFriendHolder) {
      if (consersation.getListMessageData().get(position).text != null) {
        ((ItemMessageFriendHolder) holder).txtContent.setVisibility(View.VISIBLE);
        ((ItemMessageFriendHolder) holder).txtContent.setText(consersation.getListMessageData().get(position).text);
      } else {
        if (((ItemMessageFriendHolder) holder).txtContent != null)
          ((ItemMessageFriendHolder) holder).txtContent.setVisibility(View.INVISIBLE);
      }
      if (consersation.getListMessageData().get(position).fileModel != null) {
        ((ItemMessageFriendHolder) holder).imageContent.setVisibility(View.VISIBLE);

        Glide.with(((ItemMessageFriendHolder) holder).imageContent.getContext())
          .load(consersation.getListMessageData().get(position).fileModel.url_file)
          .into(((ItemMessageFriendHolder) holder).imageContent);

//        ((ItemMessageUserHolder) holder).imageContent.setImageURI(Uri.parse(consersation.getListMessageData().get(position).fileModel.url_file));
      } else {
        if (((ItemMessageFriendHolder) holder).imageContent != null)
          ((ItemMessageFriendHolder) holder).imageContent.setVisibility(View.INVISIBLE);
      }
      Bitmap currentAvata = bitmapAvata.get(consersation.getListMessageData().get(position).idSender);
      if (currentAvata != null) {
        ((ItemMessageFriendHolder) holder).avata.setImageBitmap(currentAvata);
      } else {
        final String id = consersation.getListMessageData().get(position).idSender;
        if (bitmapAvataDB.get(id) == null) {
          bitmapAvataDB.put(id, FirebaseDatabase.getInstance().getReference().child("user/" + id + "/avata"));
          bitmapAvataDB.get(id).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
              if (dataSnapshot.getValue() != null) {
                String avataStr = (String) dataSnapshot.getValue();
                if (!avataStr.equals(StaticConfig.STR_DEFAULT_BASE64)) {
                  byte[] decodedString = Base64.decode(avataStr, Base64.DEFAULT);
                  ChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length));
                } else {
                  ChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avata));
                }
                notifyDataSetChanged();
              }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
          });
        }
      }
    } else if (holder instanceof ItemMessageUserHolder) {
      if (bitmapAvataUser != null) {
        ((ItemMessageUserHolder) holder).avata.setImageBitmap(bitmapAvataUser);
      }


      if (consersation.getListMessageData().get(position).text != null) {
        ((ItemMessageUserHolder) holder).txtContent.setVisibility(View.VISIBLE);
        ((ItemMessageUserHolder) holder).txtContent.setText(consersation.getListMessageData().get(position).text);
      } else {
        if (((ItemMessageUserHolder) holder).txtContent != null)
          ((ItemMessageUserHolder) holder).txtContent.setVisibility(View.INVISIBLE);
      }
      if (consersation.getListMessageData().get(position).fileModel != null) {
        ((ItemMessageUserHolder) holder).imageContent.setVisibility(View.VISIBLE);

        Glide.with(((ItemMessageUserHolder) holder).imageContent.getContext())
          .load(consersation.getListMessageData().get(position).fileModel.url_file)
          .into(((ItemMessageUserHolder) holder).imageContent);

//        ((ItemMessageUserHolder) holder).imageContent.setImageURI(Uri.parse(consersation.getListMessageData().get(position).fileModel.url_file));
      } else {
        if (((ItemMessageUserHolder) holder).imageContent != null)
          ((ItemMessageUserHolder) holder).imageContent.setVisibility(View.INVISIBLE);
      }
    }
  }

  @Override
  public int getItemViewType(int position) {
    if (consersation.getListMessageData().get(position).text == null && consersation.getListMessageData().get(position).fileModel != null) {
      return consersation.getListMessageData().get(position).idSender.equals(StaticConfig.UID)
        ? ChatActivity.VIEW_TYPE_USER_MESSAGE_FILE : ChatActivity.VIEW_TYPE_FRIEND_MESSAGE_FILE;
    }

    return consersation.getListMessageData().get(position).idSender.equals(StaticConfig.UID)
      ? ChatActivity.VIEW_TYPE_USER_MESSAGE_TEXT : ChatActivity.VIEW_TYPE_FRIEND_MESSAGE_TEXT;

  }

  @Override
  public int getItemCount() {
    return consersation.getListMessageData().size();
  }


}

class ItemMessageUserHolder extends RecyclerView.ViewHolder {
  public TextView txtContent;
  public ImageView imageContent;
  public CircleImageView avata;

  public ItemMessageUserHolder(View itemView) {
    super(itemView);
    txtContent = (TextView) itemView.findViewById(R.id.textContentUser);
    imageContent = (ImageView) itemView.findViewById(R.id.imageContentUser);
    avata = (CircleImageView) itemView.findViewById(R.id.imageView2);
  }
}

class ItemMessageFriendHolder extends RecyclerView.ViewHolder {
  public TextView txtContent;
  public CircleImageView avata;
  public ImageView imageContent;

  public ItemMessageFriendHolder(View itemView) {
    super(itemView);
    txtContent = (TextView) itemView.findViewById(R.id.textContentFriend);
    imageContent = (ImageView) itemView.findViewById(R.id.imageContentFriend);
    avata = (CircleImageView) itemView.findViewById(R.id.imageView3);
  }
}
