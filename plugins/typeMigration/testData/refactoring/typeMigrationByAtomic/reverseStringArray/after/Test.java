import java.util.concurrent.atomic.AtomicReferenceArray;

class Test {
    String[] s = new String[2];

    void foo() {
        s[0] = "";
        System.out.println(s[0]);

    }
}