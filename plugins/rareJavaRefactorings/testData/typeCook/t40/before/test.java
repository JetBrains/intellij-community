class LinkedList <T, E> {
    T t;
    E e;

    LinkedList<E, T> u;

    T get() {
        return t;
    }

    void set(E t) {
    }
}

class ListLinked <E> extends LinkedList<E, Integer> {
}

class Test {

    void f() {
        LinkedList y = new LinkedList();

        y.u.t = new Integer(3);
        y.u.e = "";
    }
}