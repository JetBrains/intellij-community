// "Use lombok @Setter for 'valid'" "true"

public class ClassWithBoolean {
  private int fieldWithoutSetter;
  private boolean valid;

  public void setValid(boolean param) {
    valid<caret> = param;
  }
}