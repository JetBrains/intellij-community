class A {
}


class B extends A {
}

class C {
   void foo(Object o) {
       if (o instanceof A || o instanceof B) {
           System.out.println("Something");
       }
   }
}