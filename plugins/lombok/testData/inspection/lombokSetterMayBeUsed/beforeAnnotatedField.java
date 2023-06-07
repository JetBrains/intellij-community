// "Use lombok @Setter for 'ClassWithAnnotatedField'" "true"

import lombok.Setter;

public class ClassWithAnnotatedField {
  private int canditateField;
  @Setter
  private int annotatedField;

  public void setCanditateField(int param) {
    canditateField<caret> = param;
  }
}