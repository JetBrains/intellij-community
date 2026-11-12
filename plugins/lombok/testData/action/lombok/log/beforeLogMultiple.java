class Test {

  private static final java.util.logging.Logger firstLog = java.util.logging.Logger.getLogger(Test.class.getName());
  private java.util.logging.Logger secondLog = java.util.logging.Logger.getLogger(Test.class.getName());

  public void logHallo() {
    firstLog.info("Hello!");
    secondLog.info("Hello!");
  }

}
