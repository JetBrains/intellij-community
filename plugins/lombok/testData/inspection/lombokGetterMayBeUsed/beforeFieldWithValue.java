// "Use lombok @Getter for 'anotherField'" "true"

import lombok.Value;

@Value
class Foo {
  private int anotherField;
  private int fieldWithoutGetter;

  public int getAnotherField() {
    return anotherField<caret>;
  }
}