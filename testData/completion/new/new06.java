public class Foo {
    public Foo() { }
    void m() {
        Foo.<caret>
        _a = new Bar();
    }
}