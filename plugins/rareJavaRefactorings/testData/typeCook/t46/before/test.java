class LinkedList <T> {
    T e;

    LinkedList<T> get() {
        return this;
    }
}

class Test {

    LinkedList f() {
        return null;
    }

    void foo(){
        f().e = "";
    }
}
