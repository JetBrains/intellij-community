package completion;

public class Foo {
    void m(Object o) {
        if (!(o instanceof String)) {
            <caret>
        }
    }
}