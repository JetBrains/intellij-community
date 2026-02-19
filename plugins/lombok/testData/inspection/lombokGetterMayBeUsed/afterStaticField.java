// "Use lombok @Getter for 'bar'" "true"

import lombok.Getter;

public class Foo {
  @Getter
  private static char bar;
  private int fieldWithoutGetter;

}