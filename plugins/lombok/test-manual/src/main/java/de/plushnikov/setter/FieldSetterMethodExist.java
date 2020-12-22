package de.plushnikov.setter;

import lombok.Setter;

public class FieldSetterMethodExist {
  @Setter
  private int int1Property;

  @Setter
  private int int2Property;

  public void setInt2Property() {

  }
}
