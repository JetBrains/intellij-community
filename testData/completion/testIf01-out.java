// Items: else, if, not, var, while
public class Foo {
    void m() {
        if (bar())<caret>
    }

    boolean bar() {
        return true;
    }
}