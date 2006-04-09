interface Int<T> {
    void method(T x);
}

class Sub implements Int<Xyz> {
    public void method(Xyz x) {
        x.inInt();
    }
}

interface Xint {
    void inInt();
}

class Xyz implements Xint {
    public void inInt() {
    }

    public void m1() {
    }
}