package com.android.barracuda.cypher;

import android.content.Context;
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

import java.math.BigInteger;

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
        PublicKeysFb pks = dataSnapshot.getValue(PublicKeysFb.class);

        if (pks == null || pks.key == null) {
          afterEncrypted.processMessage(msg);
          return;
        }

        long recKeyTs = pks.timestamp;
        Key key = checkAndGetLastKey(pks.timestamp);

        if (key == null) {
          key = KeyStorageDB.getInstance(context).getKey(pks.timestamp, msg.idReceiver);
        }

        if (key == null) {
          DHKeys ownPubKeys = PublicKeysDB.getInstance(context).getLast();
          if (ownPubKeys == null) {
            afterEncrypted.processMessage(msg);
            return;
          }

          key = checkAndGetLastKey(ownPubKeys.timestamp);

          if (key == null) {
            key = KeyStorageDB.getInstance(context).getKey(ownPubKeys.timestamp, msg.idReceiver);
          }

          if (key != null) recKeyTs = ownPubKeys.timestamp;
        }

        try {
          if (key == null) {
            key = handleAndAddKey(roomId, friendId, pks);
            msg.recKeyTs = pks.timestamp;
          } else {
            msg.recKeyTs = recKeyTs;
          }
          msg.key = key.ownPubKey.toString();
          msg.text = encryptText(msg.text, key.key.toByteArray());
          setLastKey(key);
          afterEncrypted.processMessage(msg);
        } catch (Exception e) {
          Log.e("CypherWorker", "Error", e);
        }

        setLastKey(key);
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {}
    });
  }

  @Override
  public void decrypt(Message msg, MessageActivityCallback afterDecrypted) {
    if (msg.key == null) return;
    Key key = checkAndGetLastKey(msg.recKeyTs);
    BigInteger secretKey = key == null ? KeyStorageDB.getInstance(context).getSecretKey(msg.recKeyTs, msg.idReceiver) : key.key;

    if (!msg.idSender.equals(StaticConfig.UID)) {

      if (secretKey == null) {
        BigInteger friendPublicKey = new BigInteger(msg.key);

        DHKeys ownPubKeys = PublicKeysDB.getInstance(context).getKeyByTimestamp(msg.recKeyTs);

        secretKey = new BigInteger(subArray(calcSharedSecretKey(ownPubKeys.p, ownPubKeys.prvKey, friendPublicKey).toByteArray(), 0, 32));

        setLastKey(addKey(msg.recKeyTs, msg.idReceiver, msg.friendId, friendPublicKey, ownPubKeys.pubKey, secretKey, System.currentTimeMillis()));
      }
    }

    if (secretKey == null) {
      msg.text = "Could not decrypt. Cause: no key\n" + msg.text;
      Log.e("CypherWorker", "No key");
    } else {
      try {
        msg.text = decryptText(msg.text, secretKey.toByteArray());
      } catch (Exception e) {
        msg.text = "Could not decrypt. Cause: " + e.getMessage();
        Log.e("CypherWorker", "Error", e);
      }
    }

    afterDecrypted.processMessage(msg);
  }
}
