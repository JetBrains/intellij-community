public class A {
    void test(String <caret>s) {
        System.out.println(s);
    }

    void callTest() {
        test(myMethod());
    }

    String myMethod() {
        return "";
    }
}
