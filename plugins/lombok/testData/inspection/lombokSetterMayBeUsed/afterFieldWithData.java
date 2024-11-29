// "Use lombok @Setter for 'anotherField'" "true"

import lombok.Data;

@Data
class Foo {
  private int anotherField;
  private int fieldWithoutGetter;

}