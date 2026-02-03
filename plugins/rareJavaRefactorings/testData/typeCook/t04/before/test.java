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
    
    void f(){
	Simple s = (Simple) x.get();
    }   
}
