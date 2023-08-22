class LinkedList <T, E> {
    T t;
    E e;

    T get() {
        return t;
    }

    void set(E t) {
    }
}

class ListLinked <T, E> extends LinkedList<E, T> {
}

class Test {

    void f() {
        ListLinked x = new ListLinked();
        LinkedList y = x;

        y.t = new Integer(3);
        y.e = "";
    }
}