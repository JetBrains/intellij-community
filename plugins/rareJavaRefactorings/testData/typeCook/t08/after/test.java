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
    LinkedList x;
    LinkedList y;
    
    void f(){
	y.set(x.get());
	Simple t = (Simple) x.get();
	y.set(new Test());
    }   
}
