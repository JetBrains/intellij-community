class Test {

  private static java.util.logging.Logger log = java.util.logging.Logger.getLogger(Test.class.getName());

  public void logHallo() {
    log.info("Hello!");
  }

  public static void main(String[] args) {
    Test test = new Test();
    test.logHallo();
  }

}
