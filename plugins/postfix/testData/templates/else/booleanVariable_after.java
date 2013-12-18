package templates.

public class Foo {
    void m(boolean x) {
        if (!x) {
            <caret>
        }
        value = dummyAssignment;
    }
}