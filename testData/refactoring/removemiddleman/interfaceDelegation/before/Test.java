class Test implements I {
  A myField;
  A getMyField(){
    return myField;
  }

  void foo() {
    myField.foo();
  }

  void bar(I i) {
    i.foo();
  }
}