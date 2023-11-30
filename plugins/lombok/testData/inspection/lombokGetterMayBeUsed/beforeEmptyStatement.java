// "Use lombok @Getter for 'field'" "true"

public class Foo {
  private int fieldWithoutGetter;
  /**
   * The field.
   */
  private int field;

  /**
   * Returns The field.
   *
   * @return The field
   */
  public int getField() {
    return field<caret>;;
  }
}