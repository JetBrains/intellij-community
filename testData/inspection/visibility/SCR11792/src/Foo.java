class Foo {
    private void method() {
        final class MyClass{}
        Object o = new MyClass();
    }

    private void foo() {
        method();
    }
}