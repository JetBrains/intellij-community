// "Use lombok @Setter for 'bar'" "false"

public class Foo {
  private int bar;
  private int fieldWithoutSetter;

  public void set1Bar(int param) {
    bar<caret> = param;
  }
}