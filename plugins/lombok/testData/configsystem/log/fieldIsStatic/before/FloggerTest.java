import lombok.extern.flogger.Flogger;

@CommonsLog
public class FloggerTest {

  public void logSomething(){
    log.atInfo().log("Hello World!");
  }

  public static void main(String[] args) {
    new FloggerTest().logSomething();
  }
}
