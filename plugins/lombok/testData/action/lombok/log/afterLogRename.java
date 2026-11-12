import lombok.extern.java.Log;

@Log
class Test {

    public void logHallo() {
    log.info("Hello!");
  }

  public void logLocal() {
    String log = "Hello!";
    Test.log.info(log);
  }

  public void logParameter(String log) {
    Test.log.info(log);
    Test.log.info(log);
  }

  public static void main(String[] args) {
    Test test = new Test();
    test.logHallo();
  }

}
