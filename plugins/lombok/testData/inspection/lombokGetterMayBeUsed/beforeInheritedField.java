// "Use lombok @Getter for 'bar'" "false"

public class Foo extends MotherClass {

  public class MotherClass {
    private int bar;
    private int fieldWithoutGetter;
  }

  public int getBar() {
    return bar<caret>;
  }
}