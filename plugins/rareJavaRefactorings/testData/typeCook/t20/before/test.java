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
    void f(){
	LinkedList y; 
        y.set(new Integer(3));
	Iterator i;
	i = y.iterator();
    }   
}
