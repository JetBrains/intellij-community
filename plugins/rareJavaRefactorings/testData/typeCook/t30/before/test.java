class LinkedList<T>{
    T t;
    T get(){return t;}
    void set(T t){}
}

class Simple {
}

class Test{
   void f (){
      LinkedList y = new LinkedList();
      y.set(new Integer(3));
   }
}
