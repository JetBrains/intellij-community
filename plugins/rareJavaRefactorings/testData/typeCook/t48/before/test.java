class LinkedList <T> {
    T e;

    LinkedList<T> get() {
        return this;
    }
}

class Test {

    void f(LinkedList x) {
        x.e = "";
    }

    void foo(){
        LinkedList y = new LinkedList();
        y.e = new Integer(3);
        f(y);
    }
}