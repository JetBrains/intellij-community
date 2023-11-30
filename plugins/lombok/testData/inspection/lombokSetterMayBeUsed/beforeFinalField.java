// "Use lombok @Setter for 'finalField'" "true"

public class Foo {
  private int fieldWithoutSetter;
  /**
   * The final field.
   */
  private final int finalField;

  /**
   * Sets The final field.
   *
   * @param The final field
   */
  public void setFinalField(int param) {
    finalField<caret> = param;
  }
}