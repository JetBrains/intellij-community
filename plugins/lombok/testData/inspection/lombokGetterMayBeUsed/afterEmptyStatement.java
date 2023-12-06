// "Use lombok @Getter for 'field'" "true"

import lombok.Getter;

public class Foo {
  private int fieldWithoutGetter;
  /**
   * The field.
   * -- GETTER --
   *  Returns The field.
   *
   * @return The field

   */
  @Getter
  private int field;

}