package com.android.barracuda.cypher.models;

public class PublicKeysToFb {
  public String p;
  public String g;
  public String key;
  public long timestamp;


  @Override
  public String toString() {
    return "PublicKeys{" +
      "p='" + p + '\'' +
      ", g='" + g + '\'' +
      ", key='" + key + '\'' +
      ", timestamp=" + timestamp +
      '}';
  }
}
