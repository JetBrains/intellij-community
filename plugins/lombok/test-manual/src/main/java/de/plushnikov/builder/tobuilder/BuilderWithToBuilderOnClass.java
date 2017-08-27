package de.plushnikov.builder.tobuilder;

import java.util.Collections;

@lombok.Builder(toBuilder = true)
@lombok.experimental.Accessors(prefix = "m")
public class BuilderWithToBuilderOnClass<T> {
  private String mOne, mTwo;

  @lombok.Builder.ObtainVia(method = "rrr", isStatic = true)
  private T foo;

  @lombok.Singular
  private java.util.List<T> bars;

  public static <K> K rrr(BuilderWithToBuilderOnClass<K> x) {
    return x.foo;
  }

  public static void main(String[] args) {
    BuilderWithToBuilderOnClass<String> bean = new BuilderWithToBuilderOnClass<String>("mOneParam", "mTwoParam", "fooParam", Collections.singletonList("barsParam1"));
    bean.mOne = "mOne";
    bean.mTwo = "mTwo";
    bean.foo = "foo";

    BuilderWithToBuilderOnClass.BuilderWithToBuilderOnClassBuilder<String> x = bean.toBuilder();
    System.out.println(x);

    x.one("builderOne");
    x.bar("builderBar");
    System.out.println(x);
  }
}
