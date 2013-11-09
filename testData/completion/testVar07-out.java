public class Foo {
    Foo m() {
        Foo foo = m().m();
        foo.m();
        return null;
    }
}