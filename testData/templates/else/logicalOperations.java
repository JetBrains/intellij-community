public class Foo {
    void m(boolean x, boolean y, boolean z) {
        x && y && z.else<caret>
        value = dummyAssignment;
    }
}