// "Use lombok @Getter for 'bar'" "true"

public class Foo {
  private static char bar;
  private int fieldWithoutGetter;

  public static char getBar() {
    return bar<caret>;
  }
}