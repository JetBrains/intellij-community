public class Foo {
    long size() { }
    void m() {
        Foo foo = new Foo();
        foo.<caret>
    }
}