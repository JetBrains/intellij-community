import java.util.ArrayList;

class B {
    A getA() { return new A(); };
    void test(A a) {
    }

    int method(ArrayList list) {
        A a = getA();

        test(a);
   }
}