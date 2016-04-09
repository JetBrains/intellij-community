package de.plushnikov.getter;

import lombok.Getter;

public class FieldGetterMethodExist {
  @Getter
  private int int1Property;

  @Getter
  private int int2Property;

  public void getInt2Property() {

  }
}
