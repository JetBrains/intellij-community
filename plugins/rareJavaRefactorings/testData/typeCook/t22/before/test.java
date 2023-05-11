class Iterator<T>{
  public T get(){return null;}
}

class LinkedList<T>{
    T t;
    public Iterator<T> iterator(){
      return null;
    }
}

class Simple {
}

class Test{
   LinkedList x;
   
   Test(Test t){
      x = t.x;
   }  
   
   void f (){
     x.t = new Integer(3);
   }
}
