// "Use lombok @Getter for 'bar'" "false"

public class Foo {
  private int bar;
  private int fieldWithoutGetter;

  @Deprecated
  public int getBar() {
    return bar<caret>;
  }
}