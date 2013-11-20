public class Foo {
    int length() { }
    void m() {
        Foo foo = new Foo();
        foo.<caret>
    }
}