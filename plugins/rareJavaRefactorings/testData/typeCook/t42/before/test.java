class LinkedList <T> {
    T t;

    T get() {
        return t;
    }
}


class Test {

    void f (LinkedList x){
        x.t = "";
    }

    void f() {
       LinkedList x = new LinkedList();

       f (x);
    }
}
