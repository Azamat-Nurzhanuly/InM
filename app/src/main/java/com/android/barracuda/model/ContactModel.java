package com.android.barracuda.model;

import java.util.Objects;

public class ContactModel {

  public String name;
  public String id;
  public String avata;
  public String number;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ContactModel that = (ContactModel) o;
    return Objects.equals(name, that.name) &&
      Objects.equals(number, that.number);
  }

  @Override
  public int hashCode() {

    return Objects.hash(name, number);
  }
}
