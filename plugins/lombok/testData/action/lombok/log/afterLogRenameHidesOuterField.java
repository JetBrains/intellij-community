import lombok.extern.java.Log;

class Outer {
  private static final String log = "Hello!";

  @Log
  static class Inner {

      void logOuter() {
      log.info(Outer.log);
      log.info(Outer.log);
    }

    void logLocal() {
      String log = "Local";
      Inner.log.info(log);
    }

    void logParameter(String log) {
      Inner.log.info(log);
    }

    static class Nested {
      void logOuter() {
        log.info(Outer.log);
      }
    }
  }
}
