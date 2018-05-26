package com.android.barracuda.model;

import java.util.ArrayList;


public class ListCall {
  private ArrayList<Call> listCall;

  public ArrayList<Call> getListCall() {
    return listCall;
  }

  public ListCall() {
    listCall = new ArrayList<>();
  }

  public String getAvataById(String id) {
    for (Call call : listCall) {
      if (id.equals(call.id)) {
        return call.avata;
      }
    }
    return "";
  }

  public void setListFriend(ArrayList<Call> callList) {
    this.listCall = listCall;
  }
}
