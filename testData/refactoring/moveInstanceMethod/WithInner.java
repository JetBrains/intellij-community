class Foreign {
    class Inner {}
}


public abstract class Test1 {
    void <caret>foo (Foreign f, Inner i) {
    }

    class Inner {}

    void bar () {
        foo(new Foreign(), new Inner());
    }
}