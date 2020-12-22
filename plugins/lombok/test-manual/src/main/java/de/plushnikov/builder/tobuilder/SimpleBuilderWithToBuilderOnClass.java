package de.plushnikov.builder.tobuilder;

import java.util.Collections;

@lombok.Builder(toBuilder = true)
public class SimpleBuilderWithToBuilderOnClass {
  private String one, two;

  @lombok.Builder.ObtainVia(method = "rrr", isStatic = true)
  private String foo;

  @lombok.Singular
  private java.util.List<String> bars;

  public static String rrr(SimpleBuilderWithToBuilderOnClass x) {
    return x.foo;
  }

  public static void main(String[] args) {
    SimpleBuilderWithToBuilderOnClass bean = new SimpleBuilderWithToBuilderOnClass("mOneParam", "mTwoParam", "fooParam", Collections.singletonList("barsParam1"));
    bean.one = "one";
    bean.two = "two";
    bean.foo = "foo";

    SimpleBuilderWithToBuilderOnClass.SimpleBuilderWithToBuilderOnClassBuilder x = bean.toBuilder();
    System.out.println(x);

    x.one("builderOne");
    x.bar("builderBar");
    System.out.println(x);
  }
}
