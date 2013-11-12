// Items: else, if, not, var
public class Foo {
    void m(boolean x, boolean y, boolean z) {
        if (!(foo() & y & z))<caret>
        Type t = new Type();
    }
}