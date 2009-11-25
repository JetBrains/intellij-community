public class Test {
  A f;
  int bar(){
    return f.foo(f);
  }
}
class A {
  /**
  * @param a
  */
  int foo(A a){
    return 0;
  }
}

class B extends A{}