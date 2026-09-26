// "Use lombok @Setter for 'InnerClass'" "true"

public class Foo {
  public class InnerClass<caret> {
    private int bar;

    public void setBar(int param) {
      bar = param; // Keep this comment
    }
  }
}