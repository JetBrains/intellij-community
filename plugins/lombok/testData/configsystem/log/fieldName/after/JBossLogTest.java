public class JBossLogTest {

  private static final org.jboss.logging.Logger LOGGER1 = org.jboss.logging.Logger.getLogger(JBossLogTest.class);

  public void logSomething() {
    LOGGER1.info("Hello World!");
  }

  public static void main(String[] args) {
    LOGGER1.info("Test");
    new JBossLogTest().logSomething();
  }
}
