// "Use lombok @Getter for 'creationDate'" "true"

import java.util.Date;

public class MoveJavaDoc {
  private Date creationDate;
  private int fieldWithoutGetter;

  /**
   * Returns The date.
   *
   * It's an instance field.
   *
   * @return The date
   */
  public Date getCreationDate() {
    return creationDate<caret>;
  }
}