package com.android.barracuda.model.cypher;

import java.math.BigInteger;

public class PublicKeysDb {
  public int id;
  public BigInteger p;
  public BigInteger g;
  public BigInteger pubKey;
  public BigInteger prvKey;
  public long timestamp;
}
