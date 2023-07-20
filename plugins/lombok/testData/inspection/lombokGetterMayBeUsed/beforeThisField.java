// "Use lombok @Getter for 'bar'" "true"

public class Foo {
  Long bar;
  private int fieldWithoutGetter;

  public Long getBar() {
    return this.bar<caret>;
  }
}