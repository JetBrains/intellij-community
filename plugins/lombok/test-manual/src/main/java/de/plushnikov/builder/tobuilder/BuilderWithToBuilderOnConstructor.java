package de.plushnikov.builder.tobuilder;

@lombok.experimental.Accessors(prefix = "m")
class BuilderWithToBuilderOnConstructor<T> {
  private String mOne, mTwo;

  private T foo;

  @lombok.Singular
  private java.util.List<T> bars;

  @lombok.Builder(toBuilder = true)
  public BuilderWithToBuilderOnConstructor(String mOne, @lombok.Builder.ObtainVia(field = "foo") T bar) {
  }

  public static void main(String[] args) {
    BuilderWithToBuilderOnConstructor<String> bean = new BuilderWithToBuilderOnConstructor<String>("mOneParam", "barParam");
    bean.mOne = "mOne";
    bean.mTwo = "mTwo";
    bean.foo = "foo";

    BuilderWithToBuilderOnConstructor.BuilderWithToBuilderOnConstructorBuilder<String> x = bean.toBuilder();
    System.out.println(x);

    x.mOne("builderOne");
    x.bar("builderBar");
    System.out.println(x);
  }
}
