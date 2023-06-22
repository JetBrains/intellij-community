class Iterator<T>{
  public T get(){return null;}
}

class LinkedList<T>{
    T t;
    public Iterator<T> iterator(){
      return null;
    }
    T get(){return t;}
}

class Simple {
}

class Test{
   LinkedList x;
   LinkedList y;
   
   Test(Test t){
      x = t.x;
   }  
   
   void f (){
     x.t = y.get();
     y.t = new String();
   }
}
