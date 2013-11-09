public class Foo {
    Foo m() {
        m().m().<caret>.m();
        return null;
    }
}