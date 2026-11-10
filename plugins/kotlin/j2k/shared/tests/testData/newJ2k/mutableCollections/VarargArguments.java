import java.util.*;

class A {
    void take(List<String>... lists) {
    }

    void passLocal() {
        List<String> list = new ArrayList<>();
        take(list);
    }

    void passParameter(List<String> list) {
        take(list, list);
    }
}
