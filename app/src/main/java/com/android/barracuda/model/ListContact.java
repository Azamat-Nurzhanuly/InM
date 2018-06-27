package com.android.barracuda.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;


public class ListContact {
  private Set<String> uniqueListContact;
  private ArrayList<ContactModel> listContact;


  public Set<String> getUniqueListContact() {
    return uniqueListContact;
  }

  public ArrayList<ContactModel> getListContact() {
    return listContact;
  }

  public ListContact() {
    uniqueListContact = new HashSet<>();
    listContact = new ArrayList<>();
  }

  public String getAvataById(String id) {
    for (ContactModel contact : listContact) {
      if (id.equals(contact.id)) {
        return contact.avata;
      }
    }
    return "";
  }
}
