// "Use lombok @Getter for 'InnerClass'" "true"

public class Foo {
  public class InnerClass<caret> {
    private int bar;

    public int getBar() {
      return bar; // Keep this comment
    }
  }
}