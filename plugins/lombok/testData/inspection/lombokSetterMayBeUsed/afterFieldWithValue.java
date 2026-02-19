// "Use lombok @Setter for 'anotherField'" "true"

import lombok.Value;

@Value
class Foo {
  private int anotherField;
  private int fieldWithoutGetter;

}