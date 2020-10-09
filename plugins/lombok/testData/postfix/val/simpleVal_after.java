import lombok.val;

public class Foo {
    void m(Object o) {
        val foo = String.valueOf(123);<caret>
    }
}
