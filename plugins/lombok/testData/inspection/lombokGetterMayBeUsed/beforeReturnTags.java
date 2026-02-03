// "Use lombok @Getter for 'releaseDate'" "true"

import java.util.Date;

public class Foo {
  private Date releaseDate;
  private int fieldWithoutGetter;

  /**
   * Returns The date.
   *
   * It's an instance field.
   *
   * @return The date
   * @return The value
   */
  public Date getReleaseDate() {
    return releaseDate<caret>;
  }
}