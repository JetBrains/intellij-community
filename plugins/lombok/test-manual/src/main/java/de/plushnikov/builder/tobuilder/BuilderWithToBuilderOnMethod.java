package de.plushnikov.builder.tobuilder;

@lombok.experimental.Accessors(prefix = "m")
class BuilderWithToBuilderOnMethod<T, K> {
  private String mOne, mTwo;
  private T foo;
  private K bar;

  @lombok.Singular
  private java.util.List<T> bars;

  @lombok.Builder(toBuilder = true)
  public static <Z> BuilderWithToBuilderOnMethod<Z, String> test(String mOne, @lombok.Builder.ObtainVia(field = "foo") Z bar) {
    return new BuilderWithToBuilderOnMethod<Z, String>();
  }

  public static void main(String[] args) {
    BuilderWithToBuilderOnMethod<String, String> bean = new BuilderWithToBuilderOnMethod<String, String>();
    bean.mOne = "mOne";
    bean.mTwo = "mTwo";
    bean.bar = "bar";
    bean.foo = "foo";

    BuilderWithToBuilderOnMethod.BuilderWithToBuilderOnMethodBuilder<String> x = bean.toBuilder();
    System.out.println(x);

    x.mOne("builderOne");
    x.bar("builderBar");
    System.out.println(x);
  }
}
