// IGNORE_K2
import java.util.List;

class A {
    List<String> list = null;

    void foo() {
        for (String e : list) {
            System.out.println(e);
        }
    }
}