class Outer {
  public static final java.util.logging.Logger <caret>log324 = java.util.logging.Logger.getLogger(Outer.class.getName());

  static class Inner extends Outer {
    static String log = "message";

    void test() {
      Inner.log324.info("hello");
      Outer.log324.info("hello");
    }
  }
}
