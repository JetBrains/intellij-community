// "Use lombok @Getter for 'processDate'" "true"

import java.util.Date;

public class MergeJavaDoc {
  /**
   * The process date.
   */
  private Date processDate;
  private int fieldWithoutGetter;

  /**
   * Returns The date.
   *
   * It's an instance field.
   *
   * @return The date
   */
  public Date getProcessDate() {
    return processDate<caret>;
  }
}