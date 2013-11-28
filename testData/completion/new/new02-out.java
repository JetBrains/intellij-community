// Items: new, var
public class Foo<T> {
    void m() {
        new Foo<Integer>()<caret>
    }
}