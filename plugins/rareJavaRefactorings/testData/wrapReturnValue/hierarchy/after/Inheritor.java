class Inheritor extends Test {
    Wrapper foo() {
    return new Wrapper("");
  }

  void bar(Test t) {
    String s = t.foo().getValue();
  }
}