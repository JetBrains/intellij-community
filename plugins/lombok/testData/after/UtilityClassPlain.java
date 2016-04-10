public final class UtilityClassPlain {
  private final static int CONSTANT = 5;

  private UtilityClassPlain() {
    throw new java.lang.UnsupportedOperationException("This is a utility class and cannot be instantiated")
  }

  public static int addSomething(int in) {
    return in + CONSTANT;
  }
}