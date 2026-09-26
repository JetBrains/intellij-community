public class FloggerTest {

  private static final com.google.common.flogger.FluentLogger LOGGER1 = com.google.common.flogger.FluentLogger.forEnclosingClass();

  public void logSomething(){
    LOGGER1.atInfo().log("Hello World!");
  }

  public static void main(String[] args) {
    LOGGER1.atInfo().log("Test");
    new FloggerTest().logSomething();
  }
}
