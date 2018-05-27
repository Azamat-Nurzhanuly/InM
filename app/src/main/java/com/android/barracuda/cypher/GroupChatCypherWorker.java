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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.util.*;

public class GroupChatCypherWorker extends CypherWorker {
  private List<String> friends;
  private List<Message> keyWaiting;

  public GroupChatCypherWorker(String roomId, List<String> friends, Context context) {
    super(roomId, context);
    this.friends = friends;
    this.keyWaiting = new ArrayList<>();
  }

  @Override
  public void encrypt(Message msg, MessageActivityCallback afterEncrypted) throws Exception {
    initLastKey();
    refreshLastKey();
    encryptInner(msg);
    afterEncrypted.processMessage(msg);
  }

  @Override
  public void decrypt(final Message msg, final MessageActivityCallback afterDecrypted) {
    Key key = checkAndGetLastKey(msg.keyTs);
    final BigInteger secretKey = key == null ? KeyStorageDB.getInstance(context).getSecretKey(msg.keyTs, msg.idReceiver) : key.key;
    if (key != null) setLastKey(key);

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
                setLastKey(addKey(keyMsg.timestamp, roomId, null, null, null, newKey, keyMsg.timestamp));
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

  public void refreshLastKey() {
    initLastKey();

    long now = System.currentTimeMillis();
    if (lastKey == null || lastKey.timestamp + StaticConfig.KEY_LIFETIME < now) {
      BigInteger newKey = randomKey();

      for (String friend : friends) {
        if (StaticConfig.UID.equals(friend)) continue;
        Message msg = new Message();
        msg.idSender = StaticConfig.UID;
        msg.idReceiver = roomId;
        msg.friendId = friend;
        msg.text = Base64.encodeToString(newKey.toByteArray(), Base64.DEFAULT);
        msg.timestamp = now;

        sendEncryptedNewKey(friend, msg);
      }

      setLastKey(addKey(now, roomId, null, null, null, newKey, now));
    }
  }

  private void sendEncryptedNewKey(final String friendId, final Message msg) {
    FirebaseDatabase.getInstance().getReference().child(FBaseEntities.PUBLIC_KEYS + "/" + friendId + "/1").addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        PublicKeysFb pks = dataSnapshot.getValue(PublicKeysFb.class);

        if (pks == null || pks.key == null) return;

        try {
          Key key = handlePublicKey(roomId, null, pks);
          msg.text = encryptText(msg.text, key.key.toByteArray());
          msg.key = key.ownPubKey.toString();
          msg.keyTs = pks.timestamp;
          sendMessageTo(msg, FBaseEntities.GR_KEY + "/" + roomId + "/" + friendId);
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
    byte arr[] = new byte[32];
    new Random().nextBytes(arr);
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
    if (this.lastKey == null)
      setLastKey(KeyStorageDB.getInstance(context).getLastKeyForRoom(roomId));
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
    FirebaseDatabase.getInstance().getReference().child(FBaseEntities.GR_KEY + "/" + roomId + "/" + StaticConfig.UID + "/" + key).removeValue();
  }
}
