// "Use lombok @Setter for 'bar'" "true"

class Foo {
  private int bar, foo;

  public void setBar(int bar) {
    this.bar = bar;<caret>
  }

}