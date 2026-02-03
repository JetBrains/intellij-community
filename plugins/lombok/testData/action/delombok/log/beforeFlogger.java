@lombok.extern.flogger.Flogger
class Test {

  public void logHallo() {
    log.atInfo().log("Hello!");
  }

  public static void main(String[] args) {
    Test test = new Test();
    test.logHallo();
  }

}
