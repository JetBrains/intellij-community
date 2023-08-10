// "Use lombok @Getter for 'bar'" "true"

import lombok.Getter;

public class Foo {
  @Getter
  Long bar;
  private int fieldWithoutGetter;

}