public class Foo<T> extends jave.lang.Throwable {
    void m() {
        throw new Foo<Foo>(<caret>);
    }
}