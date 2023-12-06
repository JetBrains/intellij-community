// "Use lombok @Setter for 'bar'" "true"

import lombok.Setter;

public class Foo {
  @Setter
  private static char bar;
  private int fieldWithoutSetter;

}