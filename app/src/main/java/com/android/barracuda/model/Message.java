package com.android.barracuda.model;


import java.util.Objects;

public class Message {

  public String idSender;
  public String idReceiver;
  public String text;
  public FileModel fileModel;
  public ContactModel contact;
  public Boolean incognito = false;
  public long timestamp;
  public Long lifeTime = 30L;

  public String date;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Message message = (Message) o;

    if (timestamp != message.timestamp) return false;
    if (!Objects.equals(lifeTime, message.lifeTime)) return false;
    if (idSender != null ? !idSender.equals(message.idSender) : message.idSender != null)
      return false;
    if (idReceiver != null ? !idReceiver.equals(message.idReceiver) : message.idReceiver != null)
      return false;
    if (text != null ? !text.equals(message.text) : message.text != null) return false;
    if (fileModel != null ? !fileModel.equals(message.fileModel) : message.fileModel != null)
      return false;
    return incognito != null ? incognito.equals(message.incognito) : message.incognito == null;
  }

  @Override
  public int hashCode() {
    long result = idSender != null ? idSender.hashCode() : 0;
    result = 31 * result + (idReceiver != null ? idReceiver.hashCode() : 0);
    result = 31 * result + (text != null ? text.hashCode() : 0);
    result = 31 * result + (fileModel != null ? fileModel.hashCode() : 0);
    result = 31 * result + (incognito != null ? incognito.hashCode() : 0);
    result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
    result = 31 * result + lifeTime;
    return (int) result;
  }
}