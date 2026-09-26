// "Use lombok @Setter for 'bar'" "false"

public class Foo {
  private char bar;
  private int fieldWithoutSetter;

  public void setBar(int param) {
    bar<caret> = param;
  }
}