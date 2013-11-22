// Items: bar, arg, cast, else, field, for, fori, forr, if, not, notnull, null, par, return, switch, throw, var, while
public class Foo {
    private Foo foo;

    public void bar(Foo arg) {
        foo = arg;<caret>
    }
}