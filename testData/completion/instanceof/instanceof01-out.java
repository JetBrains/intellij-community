public class Foo {
    void m() {
        Object o = null;
        o instanceof  ? (() o)<caret> : null;
    }
    class Foo {}
}