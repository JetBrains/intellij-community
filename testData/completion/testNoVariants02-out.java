// Items: someCoolValues.clone, someCoolValues.length, someCoolValues.arg, someCoolValues.for, someCoolValues.notnull, someCoolValues.null
public class Foo {
    void m() {
        Integer[] someCoolValues = { 1, 2, 3 };
        if (someCoolValues != null)<caret>
    }
}