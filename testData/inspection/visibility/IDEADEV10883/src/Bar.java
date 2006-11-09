public class Bar {
  public static boolean execute(boolean isFoo) {
    if (isFoo) {
      return new Foo().isTransparentsOnly();
    }
    return false;
  }

  boolean isTransparentsOnly() {
    return false;
  }
}