import lombok.CustomLog;

@CustomLog(topic = "outer")
public class CustomLogTest {
  public void logSomething() {
    log.info("Hello World!");
  }

  public static void main(String[] args) {
    log.info("Test");
    new CustomLogTest().logSomething();
    new Inner().logSomething();
  }

  @CustomLog
  static class Nested {
    public void logSomething() {
      log.info("Hello World from Nested!");
    }
  }
}

