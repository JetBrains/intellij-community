import java.util.logging.Logger;

class Test {

  private static final Logger log = Logger.getLogger(Test.class.getName());

  public void logHallo() {
    log.info("Hello!");
  }

  public static void main(String[] args) {
    Test test = new Test();
    test.logHallo();
  }

}
