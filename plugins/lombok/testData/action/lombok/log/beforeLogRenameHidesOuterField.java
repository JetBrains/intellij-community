class Outer {
  private static final String log = "Hello!";

  static class Inner {
    private static final java.util.logging.Logger <caret>log324 = java.util.logging.Logger.getLogger(Inner.class.getName());

    void logOuter() {
      log324.info(log);
      log324.info(Outer.log);
    }

    void logLocal() {
      String log = "Local";
      log324.info(log);
    }

    void logParameter(String log) {
      log324.info(log);
    }

    static class Nested {
      void logOuter() {
        log324.info(log);
      }
    }
  }
}
