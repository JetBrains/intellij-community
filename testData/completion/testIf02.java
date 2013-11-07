package completion;

public class Foo {
    void m() {
        bar().i<caret>
    }

    Boolean bar() {
        return true;
    }
}