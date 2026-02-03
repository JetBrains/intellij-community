// "Use lombok @Getter for 'bar'" "false"

public class Foo {
  private static int bar;
  private int fieldWithoutGetter;

  public int getBar() {
    return bar<caret>;
  }
}