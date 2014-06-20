class C{}

class A {}

class B extends A {}

class Test {
   void foo(C o) {
     if (o instanceof B){}
   }
}