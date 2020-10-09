import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggerCompletionTest {

  public void logSomething() {
    LOGGER1.info("Hello World!");
  }

  public static void main(String[] args) {
        LOGGER1.info("Test");
        <caret>
        new LoggerCompletionTest().logSomething();
  }
}
