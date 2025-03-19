// "Use lombok @Getter for 'bar'" "true"

class Foo {
  private int bar, foo;

  public int getBar() {
    return bar;<caret>
  }

}