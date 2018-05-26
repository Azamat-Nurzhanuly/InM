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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GroupChatCypherWorker extends CypherWorker {
  private List<CharSequence> friends;
  private String messageEntity;

  public GroupChatCypherWorker(String roomId, List<CharSequence> friends, Context context) {
    super(roomId, context);
    this.friends = friends;
    this.messageEntity = "message/" + roomId;
  }

  @Override
  public void encrypt(Message msg, MessageActivityCallback afterEncrypted) throws Exception {
    initLastKey();
    Key key = lastKey;
    refreshLastKey(key);
    encryptInner(msg);
    afterEncrypted.processMessage(msg);
  }

  @Override
  public void decrypt(final Message msg, final MessageActivityCallback afterDecrypted) {
    Key key = checkAndGetLastKey(msg.recKeyTs);
    final BigInteger secretKey = key == null ? KeyStorageDB.getInstance(context).getSecretKey(msg.recKeyTs, msg.idReceiver) : key.key;
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
              keyMsg.recKeyTs = (long) entry.getValue().get("recKeyTs");
              keyMsg.timestamp = (long) entry.getValue().get("timestamp");

              try {
                BigInteger newKey = encryptKeyFromMessage(keyMsg);
                if (newKey == null) continue;
                setLastKey(addKey(newKey(keyMsg.timestamp, roomId, null, null, null, newKey, keyMsg.timestamp)));
                removeKey(entry.getKey());
                if (keyMsg.timestamp == msg.recKeyTs) {
                  try {
                    msg.text = decryptText(msg.text, newKey.toByteArray()) + "\nEncrypted: " + msg.text;
                  } catch (Exception e) {
                    msg.text = "Could not decrypt.\n" + e.getMessage();
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
            msg.text = "Could not decrypt. Cause: no key\n" + msg.text;
            afterDecrypted.processMessage(msg);
          }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
        }
      });
    } else {

      try {
        msg.text = decryptText(msg.text, secretKey.toByteArray()) + "\nEncrypted: " + msg.text;
      } catch (Exception e) {
        msg.text = "Could not decrypt.\n" + e.getMessage();
        Log.e("CypherWorker", "Error", e);
      }

      afterDecrypted.processMessage(msg);
    }
  }

  //*******************************************************************************************************

  private void refreshLastKey(Key key) {
    initLastKey();

    long now = System.currentTimeMillis();
    if (key == null || key.timestamp + StaticConfig.KEY_LIFETIME < now) {
      BigInteger newKey = randomKey();

      for (CharSequence friend : friends) {
        if (StaticConfig.UID.equals(friend.toString())) continue;
        Message msg = new Message();
        msg.idSender = StaticConfig.UID;
        msg.idReceiver = roomId;
        msg.friendId = friend.toString();
        msg.text = Base64.encodeToString(newKey.toByteArray(), Base64.DEFAULT);
        msg.timestamp = now;

        sendEncryptedNewKey(friend.toString(), msg);
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
          msg.recKeyTs = pks.timestamp;
          sendMessageTo(msg, FBaseEntities.GR_KEY + "/" + roomId + "/" + friendId);
        } catch (Exception e) {
          Log.e("CypherWorker", "Error", e);
        }
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {}
    });
  }

  protected void sendMessageTo(Message msg, String entity) {
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
      msg.recKeyTs = lastKey.timestamp;
    }
  }

  private void initLastKey() {
    if (this.lastKey == null)
      setLastKey(KeyStorageDB.getInstance(context).getLastKeyForRoom(roomId));
  }

  private BigInteger encryptKeyFromMessage(Message msg) throws Exception {
    if (msg.key == null) return null;
    BigInteger friendPublicKey = new BigInteger(msg.key);
    DHKeys ownPubKeys = PublicKeysDB.getInstance(context).getKeyByTimestamp(msg.recKeyTs);
    BigInteger secretKey = new BigInteger(subArray(calcSharedSecretKey(ownPubKeys.p, ownPubKeys.prvKey, friendPublicKey).toByteArray(), 0, 32));

    return new BigInteger(decryptKey(msg.text, secretKey.toByteArray()));
  }

  protected byte[] decryptKey(String encrypted, byte[] key) throws Exception {
    SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
    @SuppressLint("GetInstance") Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.DECRYPT_MODE, skeySpec);

    return Base64.decode(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT)), Base64.DEFAULT);
  }

  private void removeKey(String key) {
    FirebaseDatabase.getInstance().getReference().child(FBaseEntities.GR_KEY + "/" + roomId + "/" + StaticConfig.UID + "/" + key).removeValue();
  }
}
