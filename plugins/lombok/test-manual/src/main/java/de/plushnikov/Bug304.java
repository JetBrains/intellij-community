package de.plushnikov;

import de.plushnikov.fielddefault.FieldDefaultsPublic;

public class Bug304 {
  public static void main(String[] args) {

    FieldDefaultsPublic fieldDefaultsPublic = new FieldDefaultsPublic();
    fieldDefaultsPublic.x = 1;
    fieldDefaultsPublic.q = 1.1f;
  }
}
