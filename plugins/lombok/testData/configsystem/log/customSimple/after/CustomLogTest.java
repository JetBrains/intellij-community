public class CustomLogTest {
  private static final MyLogger log = MyLogger.create();

  public void logSomething() {
    log.info("Hello World!");
  }

  public static void main(String[] args) {
    log.info("Test");
    new CustomLogTest().logSomething();
  }
}
