package de.plushnikov.fielddefault;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PUBLIC)
public class FieldDefaultsPublic {
  int x;
  int y;
  String z;

  public float q;
}
