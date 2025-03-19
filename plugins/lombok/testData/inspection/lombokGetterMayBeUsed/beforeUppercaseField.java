// "Use lombok @Getter for 'Bar'" "true"

import java.util.Date;

public class Foo {
  private Date Bar;
  private int fieldWithoutGetter;

  /**
   * Returns The date.
   *
   * @return The date
   */

  public Date getBar() {
    return Bar<caret>;
  }
}