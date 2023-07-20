// "Use lombok @Getter for 'bar'" "true"

package baz;

import lombok.Getter;

public class FullyQualifiedClass {
  @Getter
  private int bar;
  private int fieldWithoutGetter;

}