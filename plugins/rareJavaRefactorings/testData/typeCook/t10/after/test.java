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
    LinkedList[] x;
    LinkedList y;
    
    void f(){
	x[0].set (new Simple());
	x[1].set (new Test());
    }   
}
