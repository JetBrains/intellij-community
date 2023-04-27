class LinkedList <T, E> {
    T t;
    E e;

    T get() {
        return t;
    }

    void set(E t) {
    }
}

class ListLinked <E> extends LinkedList<E, Integer> {
}

class Test {

    LinkedList f (LinkedList x){
        return x;
    }

    void f() {
        ListLinked x = new ListLinked();
        LinkedList y = x;

        y.t = new Integer(3);
        y = f (y);
    }
}