class LinkedList <T> {
    T e;

    void set(LinkedList<T> t) {
        e=t.e;
    }
}

class Test {

    void f() {
        LinkedList y = new LinkedList();
        LinkedList<Integer> z=null;

        y.set(z);
    }
}
