// "Use lombok @Setter for 'processDate'" "true"

import java.util.Date;

public class MergeJavaDoc {
  /**
   * The process date.
   */
  private Date processDate;
  private int fieldWithoutSetter;

  /**
   * Sets The date.
   *
   * It's an instance field.
   *
   * @param The date
   */
  public void setProcessDate(Date param) {
    processDate<caret> = param;
  }
}