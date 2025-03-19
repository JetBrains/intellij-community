// "Use lombok @Setter for 'Foo'" "false"

public class Foo {
  private int count;

  public void setCount(int increment) {
    count<caret> += increment;
  }
}