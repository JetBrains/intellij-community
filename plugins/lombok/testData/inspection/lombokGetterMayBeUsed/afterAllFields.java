// "Use lombok @Getter for 'Foo'" "true"

import lombok.Getter;

@Getter
public class Foo {
  private int bar;
  private boolean baz;

}