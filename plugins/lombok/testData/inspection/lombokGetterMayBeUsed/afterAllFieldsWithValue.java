// "Use lombok @Getter for 'Foo'" "true"

import lombok.Value;

@Value
class Foo {
  private int bar;
  private boolean baz;

}