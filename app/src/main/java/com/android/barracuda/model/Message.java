package com.android.barracuda.model;


public class Message {

  public String idSender;
  public String idReceiver;
  public String text;
  public boolean encrypted = false;
  public FileModel fileModel;
  public long timestamp;
}