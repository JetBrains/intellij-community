public class J {
    void foo(int a, int b, long l, short s) {
        for (int i = 0; i < Math.max(a, b); i++) { };
        for (int i = 0; i <= Math.max(a, b); i++) { };
        for (int i = 10; i >= Math.max(a, b); i--) { };

        // OK with integral types
        for (int i = 0; i < l; i++) { };
        for (int i = 0; i <= s; i++) { };
    }
}