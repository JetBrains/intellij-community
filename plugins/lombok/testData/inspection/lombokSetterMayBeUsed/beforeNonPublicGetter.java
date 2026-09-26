// "Use lombok @Setter for 'bar'" "false"

public class Foo {
  private int bar;
  private int fieldWithoutSetter;

  void setBar(int param) {
    bar<caret> = param;
  }
}