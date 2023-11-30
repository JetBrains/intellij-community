// "Use lombok @Setter for 'valid'" "true"

import lombok.Setter;

public class ClassWithBoolean {
  private int fieldWithoutSetter;
  @Setter
  private boolean valid;

}