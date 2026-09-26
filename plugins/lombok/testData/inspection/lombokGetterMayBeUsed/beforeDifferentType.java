// "Use lombok @Getter for 'bar'" "false"

public class Foo {
  private char bar;
  private int fieldWithoutGetter;

  public int getBar() {
    return bar<caret>;
  }
}