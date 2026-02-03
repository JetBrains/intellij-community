class Test {
    Wrapper foo() {
    return new Wrapper("");
  }

  void bar() {
    String s = foo().getMyField();
  }

}