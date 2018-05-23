package com.android.barracuda.cypher;

import android.content.Context;
import com.android.barracuda.cypher.exceptions.NoKeyException;
import com.android.barracuda.cypher.models.Key;
import com.android.barracuda.data.KeyStorageDB;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.Message;

import java.math.BigInteger;
import java.util.List;

public class GroupChatCypherWorker extends CypherWorker {
  private List<CharSequence> friends;

  public GroupChatCypherWorker(String roomId, List<CharSequence> friends, Context context) {
    super(roomId, context);
    this.friends = friends;
  }

  @Override
  public void encryptAndSend(Message msg) {
    if (this.lastKey == null)
      initLastKey();
    Key key = lastKey;

  }

  private void initLastKey() {
    setLastKey(KeyStorageDB.getInstance(context).getLastKeyForRoom(roomId));
  }

  @Override
  public void decrypt(Message msg) throws NoKeyException {
    Key key = checkAndGetLastKey(msg.recKeyTs);
    BigInteger secretKey = key == null ? KeyStorageDB.getInstance(context).getSecretKey(msg.recKeyTs, msg.idReceiver) : key.key;

    if (secretKey == null) throw new NoKeyException("No key");

    try {
      msg.text = decryptText(msg.text, secretKey.toByteArray()) + "\nEncrypted: " + msg.text;
    } catch (Exception e) {
      if (e instanceof NoKeyException) throw (NoKeyException) e;
      e.printStackTrace();
    }

    if (msg.key != null) {
      addKey(msg.timestamp, roomId, null, null, null, new BigInteger(msg.text), System.currentTimeMillis());
      msg.text = "Got new key";
    }

  }
}
