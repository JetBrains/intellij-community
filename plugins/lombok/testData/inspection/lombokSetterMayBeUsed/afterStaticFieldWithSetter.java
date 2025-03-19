// "Use lombok @Setter for 'Foo'" "false"

import lombok.Setter;

public class Foo<caret> {
  @Setter
  private static char bar;

}