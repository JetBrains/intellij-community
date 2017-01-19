package de.plushnikov.tostring;

import lombok.ToString;

import java.util.Date;

@ToString(doNotUseGetters = true, callSuper = false, of = {"floatProperty"})
public class ClassToString extends Date {
  private int intProperty;

  private float floatProperty;
  private float[] floatPropertyArray;

  private String stringProperty;
  private String[] stringPropertyArray;

  private static String staticStringProperty;
  private static String[] staticStringPropertyArray;

  @ToString
  static class InnerStaticClass {
    String  someProperty;
  }

  @ToString
  class InnerClass {
    String someProperty;
  }

  public static void main(String[] args) {
    System.out.println(new ClassToString());
    System.out.println(new InnerStaticClass());
    System.out.println(new ClassToString().new InnerClass());
  }
}
