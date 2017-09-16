import lombok.experimental.var;

public class Foo {
    void m(Object o) {
        var foo = String.valueOf(123);<caret>
    }
}
