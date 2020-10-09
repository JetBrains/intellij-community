package de.plushnikov.getter;

import lombok.Getter;

public class FieldStaticGetter {
  @Getter
  private static int intProperty;

  public static float getFloat() {
    return 20.0f;
  }

  @Getter
  private final int staticProperty = 10;

  @Getter
  private final static int static_finalProperty = 20;

  public static void main(String[] args) {
    FieldStaticGetter bean = new FieldStaticGetter();
    System.out.println(bean.getIntProperty());
    System.out.println(getIntProperty());
    System.out.println(FieldStaticGetter.getIntProperty());
    System.out.println(bean.getStaticProperty());
    System.out.println(bean.getStatic_finalProperty());
  }
}
