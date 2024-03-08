// IGNORE_K2
class A {
    synchronized void <caret>foo() {
        bar();
    }

    void bar() {
    }
}
