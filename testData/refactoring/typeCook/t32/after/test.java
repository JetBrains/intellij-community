class LinkedList<T>{
    T t;
    T get(){return t;}
    void set(T t){}
}

class A {
}

class B extends A {
}

class C extends A {
}

class Test{
   void f (){
      LinkedList y = new LinkedList();
      B b = (B) y.get();
      C c = (C) y.get();
   }
}
