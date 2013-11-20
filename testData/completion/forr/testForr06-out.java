// Items: m, count, arg, fori, forr, var
public class Foo {
    short count() { }
    void m() {
        for (short i = new Foo().count() - 1; i >= 0; i--)<caret>
    }
}