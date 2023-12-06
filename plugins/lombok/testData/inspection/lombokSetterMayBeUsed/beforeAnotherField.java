// "Use lombok @Setter for 'bar'" "false"

public class Foo {
  private int anotherField;
  private int fieldWithoutSetter;

  public void setBar(int param) {
    anotherField<caret> = param;
  }
}