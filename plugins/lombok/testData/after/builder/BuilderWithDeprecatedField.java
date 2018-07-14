public class BuilderWithDeprecatedField {
  private String bar;

  @Deprecated
  private String foo;

  @Deprecated
  private java.util.List<String> xyzs;

  @java.beans.ConstructorProperties({"bar", "foo", "xyzs"})
  BuilderWithDeprecatedField(String bar, String foo, java.util.List<String> xyzs) {
    this.bar = bar;
    this.foo = foo;
    this.xyzs = xyzs;
  }

  public static BuilderWithDeprecatedFieldBuilder builder() {
    return new BuilderWithDeprecatedFieldBuilder();
  }

  public static class BuilderWithDeprecatedFieldBuilder {
    private String bar;
    private String foo;
    private java.util.ArrayList<String> xyzs;

    BuilderWithDeprecatedFieldBuilder() {
    }

    public BuilderWithDeprecatedFieldBuilder bar(String bar) {
      this.bar = bar;
      return this;
    }

    @Deprecated
    public BuilderWithDeprecatedFieldBuilder foo(String foo) {
      this.foo = foo;
      return this;
    }

    @Deprecated
    public BuilderWithDeprecatedFieldBuilder xyz(String xyz) {
      if (this.xyzs == null) this.xyzs = new java.util.ArrayList<String>();
      this.xyzs.add(xyz);
      return this;
    }

    @Deprecated
    public BuilderWithDeprecatedFieldBuilder xyzs(java.util.Collection<? extends String> xyzs) {
      if (this.xyzs == null) this.xyzs = new java.util.ArrayList<String>();
      this.xyzs.addAll(xyzs);
      return this;
    }

    @Deprecated
    public BuilderWithDeprecatedFieldBuilder clearXyzs() {
      if (this.xyzs != null)
        this.xyzs.clear();

      return this;
    }

    public BuilderWithDeprecatedField build() {
      java.util.List<String> xyzs;
      switch (this.xyzs == null ? 0 : this.xyzs.size()) {
        case 0:
          xyzs = java.util.Collections.emptyList();
          break;
        case 1:
          xyzs = java.util.Collections.singletonList(this.xyzs.get(0));
          break;
        default:
          xyzs = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(this.xyzs));
      }

      return new BuilderWithDeprecatedField(bar, foo, xyzs);
    }

    public String toString() {
      return "BuilderWithDeprecatedField.BuilderWithDeprecatedFieldBuilder(bar=" + this.bar + ", foo=" + this.foo + ", xyzs=" + this.xyzs + ")";
    }
  }
}
