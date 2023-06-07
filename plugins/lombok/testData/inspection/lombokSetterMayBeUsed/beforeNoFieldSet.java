// "Use lombok @Setter for 'bar'" "false"

public class Foo {
  Long bar;

  public void setBar(Long bar) {
    bar<caret> = bar;
  }
}