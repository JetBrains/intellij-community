public class BuilderWithDeprecatedParam {

  private static java.util.Collection<String> creator(String bar, @Deprecated String foo) {
    return java.util.Arrays.asList(bar, foo);
  }

  public static CollectionBuilder builder() {
    return new CollectionBuilder();
  }

  public static class CollectionBuilder {
    private String bar;
    private String foo;

    CollectionBuilder() {
    }

    public CollectionBuilder bar(String bar) {
      this.bar = bar;
      return this;
    }

    @Deprecated
    public CollectionBuilder foo(String foo) {
      this.foo = foo;
      return this;
    }

    public java.util.Collection<String> build() {
      return BuilderWithDeprecatedParam.creator(bar, foo);
    }

    public String toString() {
      return "BuilderWithDeprecatedParam.CollectionBuilder(bar=" + this.bar + ", foo=" + this.foo + ")";
    }
  }
}
