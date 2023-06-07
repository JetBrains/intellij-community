// "Use lombok @Setter for 'Foo'" "true"

public class Foo {
  Long bar;

  public void setBar(Long param) {
    this.bar<caret> = param;;
  }
}