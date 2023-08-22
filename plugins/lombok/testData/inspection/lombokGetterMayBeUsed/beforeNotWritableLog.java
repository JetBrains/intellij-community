// "Use lombok @Getter for 'log'" "false"

import lombok.extern.java.Log;
import java.util.logging.Logger;

@Log
public class Foo {
  private int fieldWithoutGetter;
  public Logger getLog() {
    return log<caret>;
  }
}