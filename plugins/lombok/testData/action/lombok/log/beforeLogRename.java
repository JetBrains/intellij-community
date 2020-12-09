class Test {

  private static final java.util.logging.Logger log324 = java.util.logging.Logger.getLogger(Test.class.getName());

  public void logHallo() {
    log324.info("Hello!");
  }

  public static void main(String[] args) {
    Test test = new Test();
    test.logHallo();
  }

}
