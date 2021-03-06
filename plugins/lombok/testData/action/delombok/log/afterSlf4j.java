class Test {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Test.class);

    public void logHallo() {
    log.info("Hello!");
  }

  public static void main(String[] args) {
    Test test = new Test();
    test.logHallo();
  }

}