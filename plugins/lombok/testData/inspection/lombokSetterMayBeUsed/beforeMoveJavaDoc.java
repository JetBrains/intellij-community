// "Use lombok @Setter for 'creationDate'" "true"

import java.util.Date;

public class MoveJavaDoc {
  private Date creationDate;
  private int fieldWithoutSetter;

  /**
   * Sets The date.
   *
   * It's an instance field.
   *
   * @param The date
   */
  public void setCreationDate(Date param) {
    creationDate<caret> = param;
  }
}