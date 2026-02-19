class LinkedList <T> {
    T e;

    T get() {
        return e;
    }

    void set(T t) {

    }
}



class Test {

    void foo() {
        LinkedList<LinkedList> x = null;
        LinkedList y = null;
        LinkedList z = null;

        y = x.get();
        z = x.get();

        y.set(new Integer(3));
        z.set("");
    }
}