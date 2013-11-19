public class Foo {
    boolean m(Object o) {
        if (o instanceof T1) return true;
        o instanceof T2.<caret>

        return false;
    }
}