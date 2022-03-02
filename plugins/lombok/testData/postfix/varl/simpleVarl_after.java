import lombok.var;

public class Foo {
    void m(Object o) {
        var <caret>foo = String.valueOf(123);
    }
}
