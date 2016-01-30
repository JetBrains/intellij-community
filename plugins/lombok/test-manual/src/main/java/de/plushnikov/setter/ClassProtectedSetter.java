package de.plushnikov.setter;

import lombok.AccessLevel;
import lombok.Setter;

@Setter(AccessLevel.PROTECTED)
public class ClassProtectedSetter {
  private int intProperty;
  private float floatProperty;

  private final int finalProperty = 0;
  private static int staticProperty;

  @Setter(AccessLevel.NONE)
  private int noAccessProperty;
}
