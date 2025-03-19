// "Use lombok @Getter for 'finalField'" "true"

import lombok.Getter;

public class Foo {
  private int fieldWithoutGetter;
  /**
   * The final field.
   * -- GETTER --
   *  Returns The final field.
   *
   * @return The final field

   */
  @Getter
  private final int finalField;

}