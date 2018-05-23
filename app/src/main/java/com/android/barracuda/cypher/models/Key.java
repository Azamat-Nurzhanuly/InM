package com.android.barracuda.cypher.models;


import java.math.BigInteger;
import java.util.Date;
import java.util.TimeZone;

public class Key {
  public long friendKeyTs;
  public String roomId;
  public String friendId;
  public BigInteger pubKey;
  public BigInteger ownPubKey;
  public BigInteger key;
  public long timestamp;


  public Key copy() {
    Key key = new Key();
    key.friendKeyTs = this.friendKeyTs;
    key.roomId = this.roomId;
    key.friendId = this.friendId;
    key.pubKey = this.pubKey;
    key.ownPubKey = this.ownPubKey;
    key.key = this.key;
    key.timestamp = this.timestamp;

    return key;
  }

  public static void main(String[] args) {
    long now = System.currentTimeMillis();
    System.out.println(new Date(now - TimeZone.getDefault().getOffset(now)));
  }

}
