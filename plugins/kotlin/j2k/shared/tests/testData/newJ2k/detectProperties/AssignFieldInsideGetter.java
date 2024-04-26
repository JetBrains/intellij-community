// IGNORE_K2
public class Foo {
    private String value;

    private String getValue() {
        if (true) {
            value = "new";
        }
        return value;
    }
}