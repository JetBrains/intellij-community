// "Use lombok @Getter for 'InnerClass'" "true"

public class Foo {
  public class InnerClass {
    private int bar;

    public int getBar() {
      return bar<caret>; // Keep this comment
    }
  }
}