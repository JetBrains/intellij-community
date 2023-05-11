class LinkedList <T, E> {
    T t;
    E e;

    LinkedList<E, T> get() {
        return this;
    }
}

class Test {
  LinkedList x;

  void foo(){
    x.get().t = "";
    x.get().e = new Integer(3);
  }
}