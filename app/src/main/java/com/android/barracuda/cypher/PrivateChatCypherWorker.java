package com.android.barracuda.cypher;

import android.content.Context;
import android.util.Log;

import com.android.barracuda.cypher.callback.MessageActivityCallback;
import com.android.barracuda.cypher.exceptions.NoKeyException;
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

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PrivateChatCypherWorker extends CypherWorker {
  private String friendId;

  public PrivateChatCypherWorker(String roomId, String friendId, Context context) {
    super(roomId, context);
    this.friendId = friendId;
  }

  @Override
  public void encrypt(final Message msg, final MessageActivityCallback afterEncrypted) {
    FirebaseDatabase.getInstance().getReference().child(FBaseEntities.PUBLIC_KEYS + "/" + msg.friendId + "/1").addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        PublicKeysFb friendPublicKeys = dataSnapshot.getValue(PublicKeysFb.class);

        if (friendPublicKeys == null || friendPublicKeys.key == null) {
          afterEncrypted.processMessage(msg);
          return;
        }

        long keyTs = 0;

        Key key = checkAndGetLastKey(friendPublicKeys.timestamp);
        if (key == null)
          key = KeyStorageDB.getInstance(context).getKey(friendPublicKeys.timestamp, msg.idReceiver);
        if (key != null) keyTs = friendPublicKeys.timestamp;

        //********************* Try with own **************************

        if (key == null) {
          DHKeys ownPublicKeys = PublicKeysDB.getInstance(context).getLast();
          key = checkAndGetLastKey(ownPublicKeys.timestamp);
          if (key == null)
            key = KeyStorageDB.getInstance(context).getKey(ownPublicKeys.timestamp, msg.idReceiver);
          if (key != null) keyTs = ownPublicKeys.timestamp;
        }

        try {

          if (key == null) {
            key = handleAndAddKey(roomId, friendId, friendPublicKeys);
            keyTs = friendPublicKeys.timestamp;
          }

          msg.keyTs = keyTs;
          msg.key = key.ownPubKey.toString();
          msg.text = encryptText(msg.text, key.key.toByteArray());

          afterEncrypted.processMessage(msg);
          setLastKey(key);

        } catch (Exception e) {
          Log.e("CypherWorker", "Error", e);
        }
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
      }
    });
  }

  @Override
  public void decrypt(Message msg, MessageActivityCallback afterDecrypted) {
    if (msg.key != null) {

      Key key = checkAndGetLastKey(msg.keyTs);
      if (key == null) key = KeyStorageDB.getInstance(context).getKey(msg.keyTs, msg.idReceiver);

      BigInteger secretKey = (key == null) ? null : key.key;

      if (!msg.idSender.equals(StaticConfig.UID)) {
        if (secretKey == null) {
          BigInteger friendPublicKey = new BigInteger(msg.key);

          DHKeys ownPubKeys = PublicKeysDB.getInstance(context).getKeyByTimestamp(msg.keyTs);
          if (ownPubKeys != null) {
            secretKey = new BigInteger(bytesToSha256(calcSharedSecretKey(ownPubKeys.p, ownPubKeys.prvKey, friendPublicKey).toByteArray()));
            key = addKey(msg.keyTs, msg.idReceiver, msg.friendId, friendPublicKey, ownPubKeys.pubKey, secretKey, System.currentTimeMillis());
          }
        }
      }

      if (secretKey == null) {
        msg.text = "Could not decrypt. Cause: no key\n";
        Log.e(getClass().getSimpleName(), "No key", new NoKeyException(msg.toString()));
        return;
      } else {
        try {
          msg.text = decryptText(msg.text, secretKey.toByteArray());
          setLastKey(key);
        } catch (Exception e) {
          msg.text = "Could not decrypt. Cause: " + e.getMessage();
          Log.e(getClass().getSimpleName(), "Error", e);
        }
      }
    }

    afterDecrypted.processMessage(msg);
  }

}
