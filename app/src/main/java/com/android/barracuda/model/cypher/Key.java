package com.android.barracuda.model.cypher;


import java.math.BigInteger;

public class Key {
  public long friendKeyTs;
  public String roomId;
  public String friendId;
  public BigInteger pubKey;
  public BigInteger ownPubKey;
  public BigInteger key;
  public long timestamp;
}
