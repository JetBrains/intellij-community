// "Use lombok @Setter for 'Foo'" "true"

public class Foo<caret> {
  Long bar;

  public void setBar(Long param) {
    this.bar = param;;
  }
}