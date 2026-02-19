class LinkedList<T>{
    T t;
    T get(){return t;}
    void set(T t){}
}

class List extends LinkedList<Integer>{
}

class Test{
   void f (){
      LinkedList y = new List();      
   }
}
