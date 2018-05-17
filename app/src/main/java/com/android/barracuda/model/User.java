package com.android.barracuda.model;


public class User {
  public String id;
  public String name;
  public String phoneNumber;
  public String avata;
  public Status status;
  public Message message;


  public User() {
    status = new Status();
    message = new Message();
    status.isOnline = false;
    status.timestamp = 0;
    message.idReceiver = "0";
    message.idSender = "0";
    message.text = "";
    message.timestamp = 0;
  }
}
