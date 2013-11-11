public class Foo {
    Foo f;
    Foo m() {
        m().m().f.v<caret>blah();
        return null;
    }
}