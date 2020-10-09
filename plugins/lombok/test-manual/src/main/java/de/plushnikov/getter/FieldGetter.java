package de.plushnikov.getter;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class FieldGetter implements SomeInterface {
  @Getter
  private int intProperty;

  @Getter(AccessLevel.PUBLIC)
  private int publicProperty;

  @Getter(AccessLevel.PROTECTED)
  private int protectedProperty;

  @Getter(AccessLevel.PACKAGE)
  private int packageProperty;

  @Getter(AccessLevel.PRIVATE)
  private int privateProperty;

  @Getter(AccessLevel.NONE)
  private int noAccessProperty;

  @Getter
  @Setter
  @javax.annotation.Nonnull
  private Integer someInteger;

  public static void main(String[] args) {
    FieldGetter fieldGetter = new FieldGetter();
    System.out.println(fieldGetter.getSomeInteger());
    fieldGetter.setSomeInteger(123);
    System.out.println(fieldGetter.getSomeInteger());
    fieldGetter.setSomeInteger(null);
    System.out.println(fieldGetter.getSomeInteger());
  }
}
