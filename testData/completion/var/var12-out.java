// Items: arg, cast, for, not, par, var
public class Foo {
    void m() {
        int foo = 1;
        foo<caret> + 2 /* comment */; // comment2
    }
}