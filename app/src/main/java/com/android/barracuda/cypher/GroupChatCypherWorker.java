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
import com.google.firebase.database.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.util.*;

public class GroupChatCypherWorker extends CypherWorker {
  private List<String> friends;
  private Random rnd = new Random();

  public GroupChatCypherWorker(String roomId, List<String> friends, Context context) {
    super(roomId, context);
    this.friends = friends;
    runKeyListener();
  }

  public GroupChatCypherWorker(String roomId, List<String> friends, Context context, boolean refreshLastKey) {
    super(roomId, context);
    this.friends = friends;
    if (refreshLastKey) refreshLastKey();
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
    Key key = checkAndGetLastKey(msg.keyTs);
    final BigInteger secretKey = key == null ? KeyStorageDB.getInstance(context).getSecretKey(msg.keyTs, msg.idReceiver) : key.key;
    if (key != null) setLastKey(key);

    if (secretKey == null) {
      msg.text = "Could not decrypt. Cause: no key";
      afterDecrypted.processMessage(msg);

      if (false)

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
    long astanaTime = astanaTime();
    if (lastKey == null || lastKey.timestamp + StaticConfig.KEY_LIFETIME < astanaTime) {

      if (StaticConfig.UID.equals(friends.get(rnd.nextInt(friends.size())))) {

        BigInteger newKey = randomKey();

        for (String friend : friends) {
          if (StaticConfig.UID.equals(friend)) continue;
          Message msg = new Message();
          msg.idSender = StaticConfig.UID;
          msg.idReceiver = roomId;
          msg.friendId = friend;
          msg.text = Base64.encodeToString(newKey.toByteArray(), Base64.DEFAULT);
          msg.timestamp = astanaTime;

          sendEncryptedNewKey(friend, msg);
        }

        setLastKey(addKey(astanaTime, roomId, null, null, null, newKey, astanaTime));

      }
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

            if (!(lastKey != null && lastKey.timestamp == keyMsg.timestamp)) {
              setLastKey(addKey(keyMsg.timestamp, roomId, null, null, null, newKey, keyMsg.timestamp));
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
