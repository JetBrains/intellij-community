package de.plushnikov.equalshashcode;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(doNotUseGetters = true, exclude = {"intProperty"})
public class EqualsAndHashCodeClass {
  private int intProperty;

  private float floatProperty;

  private String stringProperty;

  @lombok.EqualsAndHashCode
  class EqualsAndHashCode {
    int x;
    float f;
    float f2;
    float f3;
    float f4;
    double d;
    boolean bool;
    boolean[] y;
    Object[] z;
    String a;
    String b;
  }
}
