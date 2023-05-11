class Iterator<T>{
  public T get(){return null;}
}

class LinkedList<T>{
    Iterator<LinkedList<T>> t;
}

class Simple {
}

class Test{
   LinkedList y;

   void f (){
     y.t = new Iterator<LinkedList<String>>();
   }
}
