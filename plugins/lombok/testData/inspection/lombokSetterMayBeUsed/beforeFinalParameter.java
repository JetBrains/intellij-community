// "Use lombok @Setter for 'bar'" "true"

public class Foo {
  Long bar;
  private int fieldWithoutSetter;

  public void setBar(final Long param) {
    this.bar<caret> = param;
  }
}