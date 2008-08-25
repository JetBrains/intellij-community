public class Extracted<T> {
    private final Test<T> test;
    private T myT;

    public Extracted(Test<T> test) {
        this.test = test;
    }


    void bar() {
        test.foo(myT);
    }
}
