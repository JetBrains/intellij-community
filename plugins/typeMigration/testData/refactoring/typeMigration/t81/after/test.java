class A {}

class B extends A {}

class Test {
   void foo(A o) {
     if (o instanceof B){}
   }
}