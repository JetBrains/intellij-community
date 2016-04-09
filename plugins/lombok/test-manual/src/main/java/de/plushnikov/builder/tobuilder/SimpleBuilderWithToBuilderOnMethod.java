package de.plushnikov.builder.tobuilder;

class SimpleBuilderWithToBuilderOnMethod {
  private String one, two;
  private String foo;
  private String bar;

  @lombok.Singular
  private java.util.List<String> bars;

  @lombok.Builder(toBuilder = true)
  public static SimpleBuilderWithToBuilderOnMethod test(String one, @lombok.Builder.ObtainVia(field = "foo") String bar) {
    return new SimpleBuilderWithToBuilderOnMethod();
  }

  public static void main(String[] args) {
    SimpleBuilderWithToBuilderOnMethod bean = new SimpleBuilderWithToBuilderOnMethod();
    bean.one = "one";
    bean.two = "two";
    bean.bar = "bar";
    bean.foo = "foo";

    SimpleBuilderWithToBuilderOnMethod.SimpleBuilderWithToBuilderOnMethodBuilder x = bean.toBuilder();
    System.out.println(x);

    x.one("builderOne");
    x.bar("builderBar");
    System.out.println(x);
  }
}
