// "Use lombok @Getter for 'ClassWithAnnotatedField'" "true"

import lombok.Getter;

public class ClassWithAnnotatedField {
  private int canditateField;
  @Getter
  private int annotatedField;

  public int getCanditateField() {
    return canditateField<caret>;
  }
}