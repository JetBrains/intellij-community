class A {
    void <caret>foo() {
    }
}

class B extends A {
    void boo() {
        super.foo();
    }
}
