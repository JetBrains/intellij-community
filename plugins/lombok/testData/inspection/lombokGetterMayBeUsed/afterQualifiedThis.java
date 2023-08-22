// "Use lombok @Getter for 'bar'" "true"

import lombok.Getter;

public class QualifiedClass {
    //Keep this comment
    @Getter
    private int bar;
  private int fieldWithoutGetter;

}