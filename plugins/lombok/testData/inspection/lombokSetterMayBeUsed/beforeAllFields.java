// "Use lombok @Setter for 'Foo'" "true"

public class Foo<caret> {
  private int bar;
  private boolean baz;

  public void setBar(int param) {
    bar = param;
  }

  public void setBaz(boolean param) {
    baz = param;
  }
}