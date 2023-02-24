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
      LinkedList y = null;

      Test x = new Test(null);
    }
}