// "Use lombok @Setter for 'finalField'" "true"

import lombok.Setter;

public class Foo {
  private int fieldWithoutSetter;
  /**
   * The final field.
   * -- SETTER --
   *  Sets The final field.
   *
   * @param The final field

   */
  @Setter
  private final int finalField;

}