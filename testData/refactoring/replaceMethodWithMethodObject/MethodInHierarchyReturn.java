class Test {
   class A {
     String foo(){
       return null;
     }
   }

   class B extends A {
     String fo<caret>o(){
       return null;
     }
   }

   String bar(B b) {
     return b.foo();
   }
}