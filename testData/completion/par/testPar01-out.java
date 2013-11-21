// Items: arg, cast, else, field, for, if, not, notnull, null, par, return, switch, var, while
public class Foo {
    void m(Object o) {
        ((Foo) o)<caret>
    }
}