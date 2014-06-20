class Test {
    A f;

    A bar() {
        return f.foo(f);
    }
}
class A {
    <T> T foo(T t) {
        return t;
    }
}

class B extends A {
}