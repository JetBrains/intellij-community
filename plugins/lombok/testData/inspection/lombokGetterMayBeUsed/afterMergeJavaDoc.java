// "Use lombok @Getter for 'processDate'" "true"

import lombok.Getter;

import java.util.Date;

public class MergeJavaDoc {
  /**
   * The process date.
   * -- GETTER --
   *  Returns The date.
   *  It's an instance field.
   *
   * @return The date

   */
  @Getter
  private Date processDate;
  private int fieldWithoutGetter;

}