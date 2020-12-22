package de.plushnikov.setter;

import lombok.Setter;

public class FieldStaticSetter {
  @Setter
  private int intProperty;

  @Setter
  private static int staticProperty = 10;

  @Setter
  private static int static_finalProperty = 20;
}
