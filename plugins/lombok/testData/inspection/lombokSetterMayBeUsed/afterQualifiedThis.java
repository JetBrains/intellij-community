// "Use lombok @Setter for 'bar'" "true"

import lombok.Setter;

public class QualifiedClass {
    //Keep this comment
    @Setter
    private int bar;
  private int fieldWithoutSetter;

}