import java.util.*;

class A {
    void chained() {
        List<String> a;
        List<String> b;
        a = b = Arrays.asList("x", "y");
        a.set(0, "z");
    }
}
