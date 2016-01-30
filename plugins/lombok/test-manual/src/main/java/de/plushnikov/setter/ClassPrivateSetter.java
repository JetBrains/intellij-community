package de.plushnikov.setter;

import lombok.AccessLevel;
import lombok.Setter;

@Setter(AccessLevel.PRIVATE)
public class ClassPrivateSetter {
  private int intProperty;
  private float floatProperty;

  private final int finalProperty = 0;
  private static int staticProperty;

  @Setter(AccessLevel.NONE)
  private int noAccessProperty;
}
