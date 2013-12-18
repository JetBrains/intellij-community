package templates;

public class Foo {
    void m(String s) {
        assert s.isEmpty() || s.contains("asas");<caret>
    }
}