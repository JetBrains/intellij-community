class A {
    int i;
    public A() {
        i = <selection>27</selection>;
    }
}

class B extends A {
    int k;

    public B() {
        k = 10;
    }
}

class Usage {
    A a = new B();
}