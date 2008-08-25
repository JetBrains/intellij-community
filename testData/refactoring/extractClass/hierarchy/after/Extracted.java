public class Extracted<R> {
    private final Test<R> test;
    private R myT;

    public Extracted(Test<R> test) {
        this.test = test;
    }


    void bar() {
        test.foo(myT);
    }
}
