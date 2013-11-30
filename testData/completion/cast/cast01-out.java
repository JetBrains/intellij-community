// Items: arg, cast, for, instanceof, not, par, var
public class Foo {
    void m(Object o) {
        int[] xs = (() o)<caret>;
    }
}