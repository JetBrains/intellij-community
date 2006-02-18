class Test {

    public void <caret>foo() {
        bar();
    }

    void bar() {
    }

    class A {
        void blah() {
            foo();
        }
    }
}