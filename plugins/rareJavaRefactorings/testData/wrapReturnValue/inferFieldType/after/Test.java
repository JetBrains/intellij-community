class Test {
    Wrapper<String> foo() {
    return new Wrapper<String>("");
  }

  void bar() {
    String s = foo().getMyField();
  }

}