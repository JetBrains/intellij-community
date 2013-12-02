package templates;

public class Foo {
    void m(String s) {
        if (s.isEmpty() || s.contains("asas")) {
            <caret>
        }
    }
}