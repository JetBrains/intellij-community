// "Use lombok @Getter for 'valid'" "true"

public class ClassWithBoolean {
  private int fieldWithoutGetter;
  private boolean valid;

  public boolean isValid() {
    return valid<caret>;
  }
}