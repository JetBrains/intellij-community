import java.util.concurrent.atomic.AtomicReference;

class Test {
    String s = "";

    void foo() {
        if (s == null) {
           System.out.println(s);
        }
    }
}