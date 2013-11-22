// Items: arg, assert, cast, else, field, for, if, not, notnull, null, par, return, switch, throw, var, while
public class Foo {
    void m(Object o) {
        ((Foo) o)<caret>
    }
}