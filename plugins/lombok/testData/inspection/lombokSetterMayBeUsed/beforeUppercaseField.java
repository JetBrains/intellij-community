// "Use lombok @Setter for 'Bar'" "true"

import java.util.Date;

public class Foo {
  private Date Bar;
  private int fieldWithoutSetter;

  /**
   * Sets The date.
   *
   * @param The date
   */

  public void setBar(Date param) {
    Bar<caret> = param;
  }
}