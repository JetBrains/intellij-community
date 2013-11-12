// Items: else, if, not, var
public class Foo {
    void m(boolean x, boolean y, boolean z) {
        if (!x || !y || !z)<caret>
        value = dummyAssignment;
    }
}