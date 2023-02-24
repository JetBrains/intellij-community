class LinkedList <T> {
    T e;

    LinkedList<T> get() {
        return this;
    }
}

class Test {
  LinkedList x;

  Test(Test y){
     x = y.x;
  }
}