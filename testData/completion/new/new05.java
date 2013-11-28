public class Foo {
    public Foo(int x) { }
    void m() {
        Foo.<caret>
        Bar a = new Bar();
    }
}