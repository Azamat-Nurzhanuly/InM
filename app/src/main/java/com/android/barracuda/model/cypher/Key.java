package com.android.barracuda.model.cypher;


import java.math.BigInteger;

public class Key {
  public String roomId;
  public String userId;

  public BigInteger p;
  public BigInteger g;
  public BigInteger pubKey;

  public BigInteger ownP;
  public BigInteger ownG;
  public BigInteger ownPubKey;
  public BigInteger ownPrvKey;

  public BigInteger key;

  public long timestamp;
}
