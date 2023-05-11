class LinkedList <T> {
    T e;

    LinkedList<T> get() {
        return this;
    }
}

class Aux{
  LinkedList x;
}

class Test {
    Aux a = null;

    void foo(){
      LinkedList y = a.x;

      y.e = "";
    }
}