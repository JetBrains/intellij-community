// "Use lombok @Getter for 'bar'" "true"

package baz;

public class FullyQualifiedClass {
  private int bar;
  private int fieldWithoutGetter;

  public int getBar() {
    return baz.FullyQualifiedClass.this.bar<caret>;
  }
}