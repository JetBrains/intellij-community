// "Use lombok @Setter for 'processDate'" "true"

import lombok.Setter;

import java.util.Date;

public class MergeJavaDoc {
  /**
   * The process date.
   * -- SETTER --
   *  It's an instance field.
   * @param The date

   */
  @Setter
  private Date processDate;
  private int fieldWithoutSetter;

}