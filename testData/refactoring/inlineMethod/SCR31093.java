class A {
    private void <caret>f() {}
}

class B {
    private A b;
    public void g() {
        b.f();
    }
}