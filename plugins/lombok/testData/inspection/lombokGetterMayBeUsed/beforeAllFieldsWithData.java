// "Use lombok @Getter for 'Foo'" "true"

import lombok.Data;

@Data
class Foo<caret> {
  private int bar;
  private boolean baz;

  public int getBar() {
    return bar;
  }

  public boolean isBaz() {
    return baz;
  }
}