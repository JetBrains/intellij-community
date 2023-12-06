// "Use lombok @Getter for 'ClassWithAnnotatedField'" "true"

import lombok.Getter;

public class ClassWithAnnotatedField<caret> {
  private int canditateField;
  @Getter
  private int annotatedField;

  public int getCanditateField() {
    return canditateField;
  }
}