public class Test {
  B f;
  int bar(){
    return f.foo();
  }
}
class A {
  int foo(){
    return 0;
  }
}

class B extends A{}