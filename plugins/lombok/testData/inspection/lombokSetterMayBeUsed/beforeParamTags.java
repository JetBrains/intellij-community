// "Use lombok @Setter for 'releaseDate'" "true"

import java.util.Date;

public class Foo {
  private Date releaseDate;
  private int fieldWithoutSetter;

  /**
   * Sets The date.
   *
   * It's an instance field.
   *
   * @param The date
   * @param The value
   */
  public void setReleaseDate(Date param) {
    releaseDate<caret> = param;
  }
}