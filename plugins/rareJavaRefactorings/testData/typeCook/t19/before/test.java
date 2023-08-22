class LinkedList<T>{
    T t;
    public T get(){return t;}
    public void set (T t){
	this.t = t;
    }    
}

class Simple {
}

class Test{
    LinkedList y;
    LinkedList x;
    
    void f(){
	y.set((LinkedList) x.get());
        y.set(new Integer(3));
    }   
}
