class Inheritor extends Test {
  String foo() {
    return "";
  }

  void bar(Test t) {
    String s = t.foo();
  }
}