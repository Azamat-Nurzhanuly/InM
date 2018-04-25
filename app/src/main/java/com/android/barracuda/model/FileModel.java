package com.android.barracuda.model;

public class FileModel {
  public String type;
  public String url_file;
  public String name_file;
  public String size_file;

  public FileModel() {

  }

  public FileModel(String type, String url_file, String name_file, String size_file) {
    this.type = type;
    this.url_file = url_file;
    this.name_file = name_file;
    this.size_file = size_file;
  }

  public String getType() {
    return type;
  }

  public String getUrl_file() {
    return url_file;
  }

  public String getName_file() {
    return name_file;
  }

  public String getSize_file() {
    return size_file;
  }

  public void setType(String type) {
    this.type = type;
  }

  public void setUrl_file(String url_file) {
    this.url_file = url_file;
  }

  public void setName_file(String name_file) {
    this.name_file = name_file;
  }

  public void setSize_file(String size_file) {
    this.size_file = size_file;
  }

  @Override
  public String toString() {
    return "FileModel{" +
      "type='" + type + '\'' +
      ", url_file='" + url_file + '\'' +
      ", name_file='" + name_file + '\'' +
      ", size_file='" + size_file + '\'' +
      '}';
  }
}
