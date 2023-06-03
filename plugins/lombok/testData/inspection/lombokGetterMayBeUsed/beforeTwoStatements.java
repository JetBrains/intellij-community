// "Use lombok @Getter for 'bar'" "false"

public class Foo {
  private int bar;
  private int fieldWithoutGetter;

  public int getBar() {
    bar++;
    return bar<caret>;
  }
}