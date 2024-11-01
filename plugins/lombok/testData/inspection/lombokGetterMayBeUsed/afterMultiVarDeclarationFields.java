// "Use lombok @Getter for 'bar'" "true"

import lombok.Getter;

class Foo {
  @Getter
  private int bar;
    private int foo;

}