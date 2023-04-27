class LinkedList <T> {
    T e;

    LinkedList<T> get() {
        return this;
    }
}

class Test {

    void f() {
        LinkedList y = new LinkedList();

        (y.get()).e = new Integer(3);
    }
}
