// "Use lombok @Setter for 'bar'" "false"

public class Foo {
  private int bar;

  public void setBar(int param) {
    bar<caret> = bar;
  }
}