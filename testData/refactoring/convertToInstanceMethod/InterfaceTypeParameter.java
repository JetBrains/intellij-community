interface Intf<T> {
    T get();
}

class Impl<V> implements Intf<V> {
}

class X {
    static <U> X<U> <caret>method(Intf<U> p, U value) {
        U v = p.get();
        return new X<U>(v,value);
    }
}