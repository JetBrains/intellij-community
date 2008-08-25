class Test {
  int myField;
  Test(){
    myField = 7;
  }

  void foo() {
    if (myField == 7){}
  }

  void bar() {
    foo();
  }
}