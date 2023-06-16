// "Use lombok @Getter for 'bar'" "false"

public class Foo {
  private int bar;
  private int fieldWithoutGetter;

  public final int getBar() {
    return bar<caret>;
  }
}