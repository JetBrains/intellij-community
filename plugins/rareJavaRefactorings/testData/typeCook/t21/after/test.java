class Iterator<T>{
  public T get(){return null;}
}

class LinkedList<T>{
    T t;
    public T get(){return t;}
    public void set (T t){
	this.t = t;
    }    
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
     x.set(new Integer(3));
   }
}
