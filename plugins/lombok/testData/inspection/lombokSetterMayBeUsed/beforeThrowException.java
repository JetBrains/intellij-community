// "Use lombok @Setter for 'bar'" "false"

import javax.validation.constraints.NotNull;

public class Foo {
  private Integer bar;
  private int fieldWithoutSetter;

  public void setBar(@NotNull Integer param) {
    bar<caret> = param;
  }
}