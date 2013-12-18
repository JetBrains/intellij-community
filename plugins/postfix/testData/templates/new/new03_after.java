public abstract class Foo<T, U> {
    void m() {
        new Foo<Integer, U>() {<caret>
        };
    }
}