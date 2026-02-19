// "Use lombok @Getter for 'Foo'" "true"

public class Foo<caret> {
  private int bar;
  private boolean baz;

  public int getBar() {
    return bar;
  }

  public boolean isBaz() {
    return baz;
  }
}