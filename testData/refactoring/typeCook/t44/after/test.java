class LinkedList <T> {
    T e;

    void set(LinkedList<T> t) {
        e=t.e;
    }
}

class ListInt extends LinkedList<Integer>{}

class Test {

    void f() {
        LinkedList y = new LinkedList();
        ListInt z=null;

        y.set(z);
    }
}
