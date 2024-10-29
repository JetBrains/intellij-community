package some;

public class Foo {
    static int SOME
            // some comment
            = 1;

    static void test(int n) {
        int otp = n % SOME;
    }
}