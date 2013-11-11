public class Foo {
    Foo f;
    Foo m() {
        m().m().f.<caret>();
        return null;
    }
}