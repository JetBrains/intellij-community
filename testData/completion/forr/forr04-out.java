public class Foo {
    long size() { }
    void m() {
        Foo foo = new Foo();
        for (long i = foo.size() - 1; i >= 0; i--)<caret>
    }
}