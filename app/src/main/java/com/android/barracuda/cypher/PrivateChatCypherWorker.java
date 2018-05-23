package com.android.barracuda.cypher;

import android.content.Context;
import com.android.barracuda.cypher.models.DHKeys;
import com.android.barracuda.cypher.models.Key;
import com.android.barracuda.cypher.models.PublicKeysFb;
import com.android.barracuda.data.FBaseEntities;
import com.android.barracuda.data.KeyStorageDB;
import com.android.barracuda.data.PublicKeysDB;
import com.android.barracuda.model.Message;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class PrivateChatCypherWorker extends CypherWorker {
  private String friendId;
  private Key lastKey = null;

  public PrivateChatCypherWorker(String roomId, String friendId, Context context) {
    super(roomId, context);
    this.friendId = friendId;
  }

  @Override
  public void encryptAndSend(final Message msg) {
    FirebaseDatabase.getInstance().getReference().child(FBaseEntities.PUBLIC_KEYS + "/" + msg.friendId + "/1").addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        PublicKeysFb pks = dataSnapshot.getValue(PublicKeysFb.class);

        if (pks == null || pks.key == null) {
          sendMessage(msg);
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
            sendMessage(msg);
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
            key = handlePublicKey(msg, pks);
          } else {
            msg.recKeyTs = recKeyTs;
            msg.key = key.ownPubKey.toString();
          }
          doEncrypt(msg, key);
          sendMessage(msg);
        } catch (Exception e) {
          e.printStackTrace();
        }

        setLastKey(key);
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {}
    });
  }
}
