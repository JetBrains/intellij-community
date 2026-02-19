// "Use lombok @Setter for 'bar'" "true"

public class Foo {
  private static char bar;
  private int fieldWithoutSetter;

  public static void setBar(char param) {
    bar<caret> = param;
  }
}