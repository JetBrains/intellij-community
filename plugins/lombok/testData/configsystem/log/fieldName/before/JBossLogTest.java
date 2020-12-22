import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class JBossLogTest {

  public void logSomething() {
    LOGGER1.info("Hello World!");
  }

  public static void main(String[] args) {
    LOGGER1.info("Test");
    new JBossLogTest().logSomething();
  }
}
