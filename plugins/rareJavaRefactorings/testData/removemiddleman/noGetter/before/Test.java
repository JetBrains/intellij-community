class Test {
  A myField;
  void foo(){
    myField.foo();
  }

  void bar(){
    foo();
  }

}

class A {
  void foo(){}
}