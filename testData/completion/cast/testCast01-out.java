// Items: arg, cast, for, not, var
public class Foo {
    void m(Object o) {
        int[] xs = (int[]) o<caret>;
    }
}