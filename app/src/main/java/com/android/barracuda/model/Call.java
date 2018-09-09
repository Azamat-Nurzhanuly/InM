package com.android.barracuda.model;


import android.support.annotation.NonNull;

public class Call extends User implements Comparable<Call> {
  public String callId;
  public String type;
  public Long time;

  @Override
  public int compareTo(@NonNull Call o) {
    if (time < o.time.floatValue()) {
      return 1;
    } else if (time > o.time.floatValue()) {
      return -1;
    } else {
      return 0;
    }
  }
}
