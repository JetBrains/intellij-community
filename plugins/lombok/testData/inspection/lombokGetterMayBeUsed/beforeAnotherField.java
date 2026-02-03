// "Use lombok @Getter for 'bar'" "false"

public class Foo {
  private int anotherField;
  private int fieldWithoutGetter;

  public int getBar() {
    return anotherField<caret>;
  }
}