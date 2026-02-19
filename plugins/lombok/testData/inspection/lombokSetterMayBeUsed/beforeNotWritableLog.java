// "Use lombok @Setter for 'log'" "false"

import lombok.extern.java.Log;
import java.util.logging.Logger;

@Log
public class Foo {
  private int fieldWithoutSetter;
  public void setLog(Logger param) {
    log<caret> = param;
  }
}