public enum Foo {
    A, B, C;

    void f(Foo foo) {
        foo.switch<caret>
    }
}