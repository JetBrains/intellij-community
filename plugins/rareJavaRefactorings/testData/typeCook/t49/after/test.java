class LinkedList <T> {
    T e;

    LinkedList<T> get() {
        return this;
    }
}

class Test {

    Test(LinkedList x){
    }

    void foo(){
      Test x = new Test(new LinkedList<Integer>());
    }
}