// IGNORE_K2
class A {
  void foo() {}
}

class B extends A {
  void foo() {}
}

class C extends B {
  void foo<caret>() {}
}
