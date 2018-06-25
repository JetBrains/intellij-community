public class FloggerTest {

  private final com.google.common.flogger.FluentLogger log = com.google.common.flogger.FluentLogger.forEnclosingClass();

  public void logSomething(){
    log.atInfo().log("Hello World!");
  }

  public static void main(String[] args) {
    new CommonsLogTest().logSomething();
  }
}
