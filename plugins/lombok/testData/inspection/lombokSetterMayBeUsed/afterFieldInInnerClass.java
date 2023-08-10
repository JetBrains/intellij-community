// "Use lombok @Setter for 'InnerClass'" "true"

import lombok.Setter;

public class Foo {
  @Setter
  public class InnerClass {
      // Keep this comment
      private int bar;

  }
}