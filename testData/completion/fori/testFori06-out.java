// Items: m, count, arg, fori, var
public class Foo {
    short count() { }
    void m() {
        for (short i = 0; i < new Foo().count(); i++)<caret>
    }
}