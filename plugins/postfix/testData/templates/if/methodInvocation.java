package templates;

public class Foo {
    void m() {
        bar().if<caret>
    }

    boolean bar() {
        return true;
    }
}