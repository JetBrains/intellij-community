// "Use lombok @Setter for 'InnerClass'" "true"

public class Foo {
  public class InnerClass {
    private int bar;

    public void setBar(int param) {
      bar<caret> = param; // Keep this comment
    }
  }
}