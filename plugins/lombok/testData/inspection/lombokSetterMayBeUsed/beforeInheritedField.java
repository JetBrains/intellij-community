// "Use lombok @Setter for 'bar'" "false"

public class Foo extends MotherClass {

  public class MotherClass {
    private int bar;
    private int fieldWithoutSetter;
  }

  public void setBar(int param) {
    bar<caret> = param;
  }
}