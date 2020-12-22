class Test {

    private static final org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory.getLog(Test.class);

    public void logHallo() {
    log.info("Hello!");
  }

  public static void main(String[] args) {
    Test test = new Test();
    test.logHallo();
  }

}