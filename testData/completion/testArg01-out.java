// Items: arg, else, if, not, var, while
public class Foo {
    void m() {
        (bar()<caret>)
    }

    boolean bar() {
        return true;
    }
}