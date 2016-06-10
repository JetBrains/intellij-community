class BuilderWithToBuilderOnConstructor<T> {
  public static @SuppressWarnings("all") @javax.annotation.Generated("lombok") class BuilderWithToBuilderOnConstructorBuilder<T> {
    private @SuppressWarnings("all") @javax.annotation.Generated("lombok") String one;
    private @SuppressWarnings("all") @javax.annotation.Generated("lombok") int bar;
    @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnConstructorBuilder() {
    }
    public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnConstructorBuilder<T> one(final String one) {
      this.one = one;
      return this;
    }
    public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnConstructorBuilder<T> bar(final int bar) {
      this.bar = bar;
      return this;
    }
    public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnConstructor<T> build() {
      return new BuilderWithToBuilderOnConstructor<T>(one, bar);
    }
    public @Override @SuppressWarnings("all") @javax.annotation.Generated("lombok") String toString() {
      return (((("BuilderWithToBuilderOnConstructor.BuilderWithToBuilderOnConstructorBuilder(one=" + this.one) + ", bar=") + this.bar) + ")");
    }
  }
  private String one;
  private String two;
  private T foo;
  private int bar;
  public BuilderWithToBuilderOnConstructor(String one, T bar) {
  }
  public static @SuppressWarnings("all") @javax.annotation.Generated("lombok") <T>BuilderWithToBuilderOnConstructorBuilder<T> builder() {
    return new BuilderWithToBuilderOnConstructorBuilder<T>();
  }
  public @SuppressWarnings("all") @javax.annotation.Generated("lombok") BuilderWithToBuilderOnConstructorBuilder<T> toBuilder() {
    return new BuilderWithToBuilderOnConstructorBuilder<T>().one(this.one).bar(this.foo);
  }
}
