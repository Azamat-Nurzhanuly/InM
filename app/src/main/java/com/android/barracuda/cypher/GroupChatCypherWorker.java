package com.android.barracuda.cypher;

import android.content.Context;
import com.android.barracuda.cypher.exceptions.NoKeyException;
import com.android.barracuda.model.Message;

public class GroupChatCypherWorker implements CypherWorker{
  private String roomId;
  private Context context;

  public GroupChatCypherWorker(String roomId, Context context) {
    this.roomId = roomId;
    this.context = context;
  }

  @Override
  public void encryptAndSend(Message msg) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void decrypt(Message msg) throws NoKeyException {
    throw new UnsupportedOperationException();
  }
}
