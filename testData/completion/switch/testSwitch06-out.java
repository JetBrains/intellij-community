// Items: f, arg, notnull, null, switch
public enum Foo {
    A, B, C;

    void f(Foo foo) {
        switch (foo)<caret>
    }
}