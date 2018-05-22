package com.android.barracuda.cypher;

import com.android.barracuda.cypher.exceptions.NoKeyException;
import com.android.barracuda.model.Message;

public interface CypherWorker {
  void encryptAndSend(final Message msg);

  void decrypt(Message msg) throws NoKeyException;
}
