// "Use lombok @Setter for 'bar'" "true"

import lombok.Setter;

public class Foo {
  @Setter
  Long bar;
  private int fieldWithoutSetter;

}