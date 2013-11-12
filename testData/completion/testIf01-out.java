// Items: else, if, not, var
public class Foo {
    void m() {
        if (bar())<caret>
    }

    boolean bar() {
        return true;
    }
}