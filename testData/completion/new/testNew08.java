public abstract class Foo {
    void m() {
        FooBar.<caret>
    }
}

class FooBar { private FooBar(int x) { } }