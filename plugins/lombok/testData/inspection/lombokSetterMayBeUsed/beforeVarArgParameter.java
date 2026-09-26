// "Use lombok @Setter for 'bar'" "false"

public class Foo {
  Object bar;

  public void setBar(Object param...) {
    this.bar<caret> = param;
  }
}