class A {
    int i;
    public A(int anObject) {
        i = anObject;
    }
}

class B extends A {
    B() {
        super(27);
    }
}

class Usage {
    A a = new B();
}