class BuilderWithToBuilderOnMethod<T, K> {
  public static @SuppressWarnings("all") @javax.annotation.Generated("lombok") class BuilderWithToBuilderOnMethodBuilder<Z> {
    private @SuppressWarnings("all") @javax.annotation.Generated("lombok") String one;
    private @SuppressWarnings("all") @javax.annotation.Generated("lombok") Z bar;
    @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnMethodBuilder() {
      super();
    }
    public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnMethodBuilder<Z> one(final String one) {
      this.one = one;
      return this;
    }
    public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnMethodBuilder<Z> bar(final Z bar) {
      this.bar = bar;
      return this;
    }
    public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnMethod<Z, String> build() {
      return BuilderWithToBuilderOnMethod.<Z>test(one, bar);
    }
    public @Override @SuppressWarnings("all") @javax.annotation.Generated("lombok") String toString() {
      return (((("BuilderWithToBuilderOnMethod.BuilderWithToBuilderOnMethodBuilder(one=" + this.one) + ", bar=") + this.bar) + ")");
    }
  }
  private String one;
  private String two;
  private T foo;
  private K bar;
  private int some;
  BuilderWithToBuilderOnMethod() {
    super();
  }
  public static <Z>BuilderWithToBuilderOnMethod<Z, String> test(String one, Z bar) {
    return new BuilderWithToBuilderOnMethod<Z, String>();
  }
  public static @SuppressWarnings("all") @javax.annotation.Generated("lombok") <Z>BuilderWithToBuilderOnMethodBuilder<Z> builder() {
    return new BuilderWithToBuilderOnMethodBuilder<Z>();
  }
  public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnMethodBuilder<T> toBuilder() {
    return new BuilderWithToBuilderOnMethodBuilder<T>().one(this.one).bar(this.foo);
  }
}
