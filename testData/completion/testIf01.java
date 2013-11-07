public class Foo {
    void m() {
        bar().<caret>
    }

    boolean bar() {
        return true;
    }
}