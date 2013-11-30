public class Foo {
    int length() { }
    void m() {
        Foo foo = new Foo();
        for (int i = 0; i < foo.length(); i++)<caret>
    }
}