class BuilderComplex {
  private static <T extends Number> void testVoidWithGenerics(T number, int arg2, String arg3, BuilderComplex selfRef) {
  }
  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  public static class VoidBuilder<T extends Number> {
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    private T number;
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    private int arg2;
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    private String arg3;
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    private BuilderComplex selfRef;
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    VoidBuilder() {
    }
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    public VoidBuilder<T> number(final T number) {
      this.number = number;
      return this;
    }
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    public VoidBuilder<T> arg2(final int arg2) {
      this.arg2 = arg2;
      return this;
    }
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    public VoidBuilder<T> arg3(final String arg3) {
      this.arg3 = arg3;
      return this;
    }
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    public VoidBuilder<T> selfRef(final BuilderComplex selfRef) {
      this.selfRef = selfRef;
      return this;
    }
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    //    TODO  BuilderComplex.<T>testVoidWithGenerics(number, arg2, arg3, selfRef);
    public void execute() {
      testVoidWithGenerics(number, arg2, arg3, selfRef);
    }
    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    public java.lang.String toString() {
      return "BuilderComplex.VoidBuilder(number=" + this.number + ", arg2=" + this.arg2 + ", arg3=" + this.arg3 + ", selfRef=" + this.selfRef + ")";
    }
  }
  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  public static <T extends Number> VoidBuilder<T> builder() {
    return new VoidBuilder<T>();
  }
}