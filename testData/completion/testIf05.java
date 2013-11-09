public class Foo {
    void m(boolean b, int value) {
        b.<caret>
        value = 123;
    }
}