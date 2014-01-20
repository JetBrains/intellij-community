@lombok.extern.log4j.Log4j2
class Test {

  public void logHallo() {
    log.info("Hello!");
  }

  public static void main(String[] args) {
    Test test = new Test();
    test.logHallo();
  }

}