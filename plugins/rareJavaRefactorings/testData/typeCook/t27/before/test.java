class Iterator<T>{
  public T get(){return null;}
}

class LinkedList<T>{
    LinkedList z;
    T t;
    public Iterator<T> iterator(){
      return null;
    }
    T get(){return t;}
    void set(T t){}
}

class Simple {
}

class Test{
   LinkedList y;
   
   Test(Test t){
      x = t.x;
   }  
   
   void f (){
     y.z.t = new String();
   }
}
