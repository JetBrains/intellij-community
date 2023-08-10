// "Use lombok @Getter for 'bar'" "false"

import javax.validation.constraints.NotNull;

public class Foo {
  private Integer bar;
  private int fieldWithoutGetter;

  public @NotNull Integer getBar() {
    return bar<caret>;
  }
}