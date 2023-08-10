// "Use lombok @Setter for 'bar'" "true"

package project;

import lombok.Setter;

public class OneFullyQualifiedClass {
  @Setter
  private int bar;
  private int fieldWithoutSetter;

}