import lombok.val;

public class Foo {
    void m(Object o) {
        val <caret>foo = String.valueOf(123);
    }
}
