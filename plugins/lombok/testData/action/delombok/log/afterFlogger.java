class Test {

  private static final com.google.common.flogger.FluentLogger log =
    com.google.common.flogger.FluentLogger.forEnclosingClass();
  public void logHallo() {
    log.atInfo().log("Hello!");
  }

  public static void main(String[] args) {
    Test test = new Test();
    test.logHallo();
  }

}
