package templates;

public class Foo {
    boolean m(Object o) {
        if (o instanceof T1) return true;
        if (o instanceof T2) {
            <caret>
        }

        return false;
    }
}