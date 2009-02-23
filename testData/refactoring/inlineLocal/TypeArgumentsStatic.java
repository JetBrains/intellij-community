class Example {
    private static class X<T> {}
    private static <T> X<T> foo() { return new X<T>(); }
    private static <T> X<T> boo(X<T> x) {return x;}
    private static void goo() {
        X<Integer> f = foo();
        X<Integer> x = boo(<caret>f);
    }
}