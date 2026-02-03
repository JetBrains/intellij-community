// "Use lombok @Setter for 'bar'" "false"

public class Foo {
  private static int bar;
  private int fieldWithoutSetter;

  public void setBar(int param) {
    bar<caret> = param;
  }
}