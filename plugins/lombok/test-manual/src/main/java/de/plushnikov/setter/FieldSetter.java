package de.plushnikov.setter;

import lombok.AccessLevel;
import lombok.Setter;

public class FieldSetter {
  @Setter
  private int intProperty;

  @Setter(AccessLevel.PUBLIC)
  private int publicProperty;

  @Setter(AccessLevel.PROTECTED)
  private int protectedProperty;

  @Setter(AccessLevel.PACKAGE)
  private int packageProperty;

  @Setter(AccessLevel.PRIVATE)
  private int privateProperty;

  @Setter(AccessLevel.NONE)
  private int noAccessProperty;

  @Setter
  private final int finalProperty = 0;

  @Setter(AccessLevel.NONE)
  private final int finalAccessProperty = 0;
}
