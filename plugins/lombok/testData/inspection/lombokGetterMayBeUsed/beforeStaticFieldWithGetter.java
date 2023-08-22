// "Use lombok @Getter for 'Foo'" "false"

import lombok.Getter;

public class Foo<caret> {
  @Getter
  private static char bar;

}