// Items: m, arg, cast, for, not, var
public class Foo {
    Foo m() {
        Foo foo = m().m();
        foo<caret>.m();
        return null;
    }
}