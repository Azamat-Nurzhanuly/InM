package com.android.barracuda.cypher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Base64;
import android.util.Log;
import com.android.barracuda.cypher.callback.MessageActivityCallback;
import com.android.barracuda.cypher.models.DHKeys;
import com.android.barracuda.cypher.models.Key;
import com.android.barracuda.cypher.models.PublicKeysFb;
import com.android.barracuda.data.FBaseEntities;
import com.android.barracuda.data.KeyStorageDB;
import com.android.barracuda.data.PublicKeysDB;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.Message;
import com.android.barracuda.util.AuthUtils;
import com.google.firebase.database.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.util.*;

public class GroupChatCypherWorker extends CypherWorker {
  private List<String> friends;
  private static final long KEY_RIPE_TIME = StaticConfig.TEST_MODE ? 60 * 1000 : 10 * 60 * 1000;

  public GroupChatCypherWorker(String roomId, String adminId, List<String> friends, Context context) {
    super(roomId, context);
    this.friends = friends;
    if (!haveKey()) {
      long start = System.currentTimeMillis();
      Log.d(getClass().getSimpleName(),
        "****************** Request new key start");
      requestKey();
      Log.d(getClass().getSimpleName(),
        "****************** Request new key finish in secs: " + (System.currentTimeMillis() - start) / 1000);
    } else {
      long start = System.currentTimeMillis();
      if (StaticConfig.UID.equals(adminId) && isLastKeyOld()) {
        Log.d(getClass().getSimpleName(),
          "****************** Refresh key start");
        refreshLastKey();
        Log.d(getClass().getSimpleName(),
          "****************** Refresh key finish in secs" + (System.currentTimeMillis() - start) / 1000);
      }
    }
    runKeyListener();
  }

  @Override
  public void encrypt(Message msg, MessageActivityCallback afterEncrypted) throws Exception {
    initLastKey();
    encryptInner(msg);
    afterEncrypted.processMessage(msg);
  }

  @Override
  public void decrypt(final Message msg, final MessageActivityCallback afterDecrypted) {

    if (Boolean.TRUE.equals(msg.keyReq)) {
      if (!StaticConfig.UID.equals(msg.idSender)) {
        sendCurrentKeyTo(msg.idSender);
        removeKey(msg.id);
      }
      return;
    }

    if (msg.keyTs == null || msg.keyTs == 0) {
      afterDecrypted.processMessage(msg);
      return;
    }
    initLastKey();
    Key key = checkAndGetLastKey(msg.keyTs);
    final BigInteger secretKey = key == null ? KeyStorageDB.getInstance(context).getSecretKey(msg.keyTs, msg.idReceiver) : key.key;
    if (key != null) setLastKey(key);
    else initLastKey();

    if (lastKey == null) {
      refreshLastKey();
      return;
    }

    if (secretKey == null) {

      FirebaseDatabase.getInstance().getReference()
        .child(FBaseEntities.GR_KEY + "/" + roomId + "/" + StaticConfig.UID).addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
          long count = dataSnapshot.getChildrenCount();
          boolean decrypted = false;

          if (count != 0) {
            HashMap<String, HashMap<String, Object>> value = (HashMap<String, HashMap<String, Object>>) dataSnapshot.getValue();

            for (Map.Entry<String, HashMap<String, Object>> entry : value.entrySet()) {
              Message keyMsg = new Message();
              keyMsg.idSender = (String) entry.getValue().get("idSender");
              keyMsg.idReceiver = (String) entry.getValue().get("idReceiver");
              keyMsg.friendId = (String) entry.getValue().get("friendId");
              keyMsg.key = (String) entry.getValue().get("key");
              keyMsg.text = (String) entry.getValue().get("text");
              keyMsg.keyTs = (long) entry.getValue().get("keyTs");
              keyMsg.timestamp = (long) entry.getValue().get("timestamp");

              removeKey(entry.getKey());

              try {
                BigInteger newKey = encryptKeyFromMessage(keyMsg);
                if (newKey == null) continue;
                Key keyFromFb = addKey(keyMsg.timestamp, roomId, null, null, null, newKey, keyMsg.timestamp);
                if (keyFromFb.timestamp < astanaTime())
                  setLastKey(keyFromFb);
                if (keyMsg.timestamp == msg.keyTs) {
                  try {
                    msg.text = decryptText(msg.text, newKey.toByteArray());
                  } catch (Exception e) {
                    msg.text = "Could not decrypt. Cause " + e.getMessage();
                    Log.e("CypherWorker", "Error", e);
                  }
                  afterDecrypted.processMessage(msg);
                  decrypted = true;
                }
              } catch (Exception e) {
                Log.e("CypherWorker", "Error", e);
              }
            }
          }
          if (count == 0 || !decrypted) {
            msg.text = "Could not decrypt. Cause: no key";
            afterDecrypted.processMessage(msg);
          }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
        }
      });
    } else {
      try {
        msg.text = decryptText(msg.text, secretKey.toByteArray());
      } catch (Exception e) {
        msg.text = "Could not decrypt. Cause: " + e.getMessage();
        Log.e("CypherWorker", "Error", e);
      }

      afterDecrypted.processMessage(msg);
    }
  }


  //*******************************************************************************************************

  private boolean haveKey() {
    initLastKey();
    return lastKey != null;
  }

  private boolean isLastKeyOld() {
    initLastKey();
    return lastKey == null || lastKey.timestamp + StaticConfig.KEY_LIFETIME < astanaTime();
  }

  private void requestKey() {
    Message keyRequest = new Message();
    keyRequest.idSender = StaticConfig.UID;
    keyRequest.keyReq = true;
    sendMessageTo(keyRequest, "message/" + roomId);
  }

//  private void runKeyRefreshThread() {
//    new Runnable() {
//      @Override
//      public void run() {
//        refreshLastKey();
//      }
//    }.run();
//  }

  public void refreshLastKey() {
    initLastKey();
    long keyTs = astanaTime() + KEY_RIPE_TIME;
//    if (lastKey == null || lastKey.timestamp + StaticConfig.KEY_LIFETIME < astanaTime) {

    BigInteger newKey = randomKey();

    for (String friendId : friends) {
      if (StaticConfig.UID.equals(friendId)) continue;
      Message msg = new Message();
      msg.idSender = StaticConfig.UID;
      msg.idReceiver = AuthUtils.userIdToRoomId(friendId);
      msg.friendId = friendId;
      msg.text = Base64.encodeToString(newKey.toByteArray(), Base64.DEFAULT);
      msg.timestamp = keyTs;

      sendEncryptedNewKey(friendId, msg);
    }

    addKey(keyTs, roomId, null, null, null, newKey, keyTs);
//    }
  }

  public void sendCurrentKeyTo(String friendId) {
    if (friendId == null || StaticConfig.UID.equals(friendId)) return;
    initLastKey();
    if (lastKey == null) return;
    Message msg = new Message();
    msg.idSender = StaticConfig.UID;
    msg.idReceiver = AuthUtils.userIdToRoomId(friendId);
    msg.friendId = friendId;
    msg.text = Base64.encodeToString(lastKey.key.toByteArray(), Base64.DEFAULT);
    msg.timestamp = lastKey.timestamp;

    sendEncryptedNewKey(friendId, msg);
  }

  private void sendEncryptedNewKey(final String friendId, final Message msg) {
    FirebaseDatabase.getInstance().getReference().child(FBaseEntities.PUBLIC_KEYS + "/" + friendId + "/1").addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        PublicKeysFb pks = dataSnapshot.getValue(PublicKeysFb.class);

        if (pks == null || pks.key == null) return;

        try {
          Key key = handlePublicKey(msg.idReceiver, friendId, pks);
          msg.text = encryptText(msg.text, key.key.toByteArray());
          msg.key = key.ownPubKey.toString();
          msg.keyTs = pks.timestamp;
          sendMessageTo(msg, FBaseEntities.GR_KEY + "/" + roomId + "/" + friendId);

          KeyStorageDB.getInstance(context).addKey(key);

        } catch (Exception e) {
          Log.e("CypherWorker", "Error", e);
        }
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {}
    });
  }

  private void sendMessageTo(Message msg, String entity) {
    FirebaseDatabase.getInstance().getReference().child(entity).push().setValue(msg);
  }

  private BigInteger randomKey() {
    Random rnd = new Random();
    byte arr[] = new byte[32];
    rnd.nextBytes(arr);
    BigInteger key = new BigInteger(arr);
    return key.abs();
  }

  private void encryptInner(Message msg) throws Exception {
    if (lastKey != null) {
      msg.text = encryptText(msg.text, lastKey.key.toByteArray());
      msg.keyTs = lastKey.timestamp;
    }
  }

  private void initLastKey() {
    if (this.lastKey == null || this.lastKey.timestamp < astanaTime() + StaticConfig.KEY_LIFETIME + KEY_RIPE_TIME)
      setLastKey(KeyStorageDB.getInstance(context).getLastKeyForGroupChatRoom(roomId, astanaTime()));
  }

  private BigInteger encryptKeyFromMessage(Message msg) throws Exception {
    if (msg.key == null) return null;
    BigInteger friendPublicKey = new BigInteger(msg.key);
    DHKeys ownPubKeys = PublicKeysDB.getInstance(context).getKeyByTimestamp(msg.keyTs);
    BigInteger secretKey = new BigInteger(subArray(calcSharedSecretKey(ownPubKeys.p, ownPubKeys.prvKey, friendPublicKey).toByteArray(), 0, 32));

    return new BigInteger(decryptKey(msg.text, secretKey.toByteArray()));
  }

  private byte[] decryptKey(String encrypted, byte[] key) throws Exception {
    SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
    @SuppressLint("GetInstance") Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.DECRYPT_MODE, skeySpec);

    return Base64.decode(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT)), Base64.DEFAULT);
  }

  private void removeKey(String key) {
    if (key == null) return;
    FirebaseDatabase.getInstance().getReference().child(FBaseEntities.GR_KEY + "/" + roomId + "/" + StaticConfig.UID + "/" + key).removeValue();
  }

  private void runKeyListener() {
    FirebaseDatabase.getInstance().getReference()
      .child(FBaseEntities.GR_KEY + "/" + roomId + "/" + StaticConfig.UID)
      .addChildEventListener(new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
          Message keyMsg = dataSnapshot.getValue(Message.class);
          removeKey(dataSnapshot.getKey());
          if (keyMsg == null) return;

          try {
            BigInteger newKey = encryptKeyFromMessage(keyMsg);
            Log.d("GOT NEW KEY **********", newKey.toString());
            if (!(lastKey != null && lastKey.timestamp == keyMsg.timestamp)) {
              Key key = addKey(keyMsg.timestamp, roomId, null, null, null, newKey, keyMsg.timestamp);
              Log.d("SAVED NEW KEY ******", newKey.toString());
              if (keyMsg.timestamp < astanaTime()) {
                setLastKey(key);
              }
            }

          } catch (Exception e) {
            Log.e("GroupChatCypherWorker", "Could not decrypt key", e);
          }
        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String s) {}

        @Override
        public void onChildRemoved(DataSnapshot dataSnapshot) {}

        @Override
        public void onChildMoved(DataSnapshot dataSnapshot, String s) {}

        @Override
        public void onCancelled(DatabaseError databaseError) {}
      });
  }

  private long astanaTime() {
    long now = System.currentTimeMillis();
    int gmt = TimeZone.getDefault().getOffset(now);

    return now + (StaticConfig.ASTANA_OFFSET + gmt);
  }
}
