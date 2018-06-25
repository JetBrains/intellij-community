import lombok.extern.flogger.Flogger;

@Flogger
public class FloggerTest {

  public void logSomething(){
    LOGGER1.atInfo.log("Hello World!");
  }

  public static void main(String[] args) {
    LOGGER1.atInfo.log("Test");
    new FloggerTest().logSomething();
  }
}
