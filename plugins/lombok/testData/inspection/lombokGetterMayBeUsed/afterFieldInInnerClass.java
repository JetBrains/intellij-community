// "Use lombok @Getter for 'InnerClass'" "true"

import lombok.Getter;

public class Foo {
  @Getter
  public class InnerClass {
      // Keep this comment
      private int bar;

  }
}