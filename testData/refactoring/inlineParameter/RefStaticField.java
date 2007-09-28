public class A {
    void test(String <caret>s) {
        System.out.println(s);
    }

    void callTest() {
        test(B.f);
    }
}

class B {
    public static String f = null;
}