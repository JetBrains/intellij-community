class B extends A {
    @Override
    public void foo(int value) {}
}

interface I {
    void foo(int value);
}

class C extends A implements I {
    @Override
    public void foo(int value) {}
}
