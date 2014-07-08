class C{}

class A {}

class B extends A {}

class Test {
   void foo(Object o) {
     if (o instanceof B){}
   }
}