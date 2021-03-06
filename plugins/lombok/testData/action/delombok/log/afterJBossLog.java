class Test {

  private static final org.jboss.logging.Loggerlog =org.jboss.logging.Logger.getLogger(Test.class);

  public void logHallo() {
    log.info("Hello!");
  }

  public static void main(String[] args) {
    Test test = new Test();
    test.logHallo();
  }

}