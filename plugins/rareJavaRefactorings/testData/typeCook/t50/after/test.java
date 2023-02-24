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
      LinkedList y = new LinkedList();

      Test x = new Test(y);

      y.e = new Integer(3);
    }
}