package com.android.barracuda.model;


public class Message {
  public String idSender;
  public String idReceiver;
  public String friendId;
  public String text;
  public String key;
  public Long keyTs;
  public FileModel fileModel;
  public Boolean keyReq;
  public long timestamp;

  @Override
  public String toString() {
    return "Message{" +
      "idSender='" + idSender + '\'' +
      ", idReceiver='" + idReceiver + '\'' +
      ", friendId='" + friendId + '\'' +
      ", text='" + text + '\'' +
      ", key='" + key + '\'' +
      ", keyTs=" + keyTs +
      ", fileModel=" + fileModel +
      ", timestamp=" + timestamp +
      '}';
  }
}