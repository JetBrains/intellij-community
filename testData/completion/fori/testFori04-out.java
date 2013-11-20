// Items: m, size, arg, fori, notnull, null
public class Foo {
    long size() { }
    void m() {
        Foo foo = new Foo();
        for (long i = 0; i < foo.size(); i++)<caret>
    }
}