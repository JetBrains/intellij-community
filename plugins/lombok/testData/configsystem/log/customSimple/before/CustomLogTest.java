import lombok.CustomLog;

@CustomLog
public class CustomLogTest {

  public void logSomething() {
    log.info("Hello World!");
  }

  public static void main(String[] args) {
    log.info("Test");
    new CustomLogTest().logSomething();
  }
}
