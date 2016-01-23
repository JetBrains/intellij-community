class BuilderWithToBuilderOnClass<T> {
  public static @SuppressWarnings("all") @javax.annotation.Generated("lombok") class BuilderWithToBuilderOnClassBuilder<T> {
    private @SuppressWarnings("all") @javax.annotation.Generated("lombok") String one;
    private @SuppressWarnings("all") @javax.annotation.Generated("lombok") String two;
    private @SuppressWarnings("all") @javax.annotation.Generated("lombok") T foo;
    private @SuppressWarnings("all") @javax.annotation.Generated("lombok") int bar;
    @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnClassBuilder() {
      super();
    }
    public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnClassBuilder<T> one(final String one) {
      this.one = one;
      return this;
    }
    public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnClassBuilder<T> two(final String two) {
      this.two = two;
      return this;
    }
    public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnClassBuilder<T> foo(final T foo) {
      this.foo = foo;
      return this;
    }
    public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnClassBuilder<T> bar(int bar) {
      this.bar = bar;
      return this;
    }

    public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnClass<T> build() {
      return new BuilderWithToBuilderOnClass<T>(one, two, foo, bar);
    }
    public @Override @SuppressWarnings("all") @javax.annotation.Generated("lombok") String toString() {
      return (((((((("BuilderWithToBuilderOnClass.BuilderWithToBuilderOnClassBuilder(one=" + this.one) + ", two=") + this.two) + ", foo=") + this.foo) + ", bar=") + this.bar) + ")");
    }
  }
  private String one;
  private String two;
  private T foo;
  private int bar;
  public static <K>K rrr(BuilderWithToBuilderOnClass<K> x) {
    return x.foo;
  }
  @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnClass(final String one, final String two, final T foo, final int bar;) {
    super();
    this.one = one;
    this.two = two;
    this.foo = foo;
    this.bar = bar;
  }
  public static @SuppressWarnings("all") @javax.annotation.Generated("lombok") <T>BuilderWithToBuilderOnClassBuilder<T> builder() {
    return new BuilderWithToBuilderOnClassBuilder<T>();
  }
  public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnClassBuilder<T> toBuilder() {
    return new BuilderWithToBuilderOnClassBuilder<T>().one(this.one).two(this.two).foo(BuilderWithToBuilderOnClass.rrr(this)).bar(this.bar);
  }
}
