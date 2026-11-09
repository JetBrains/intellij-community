import java.util.*;

public class J {
    void foo() {
        List<List<Integer>> list = new ArrayList<>();
        for (List<Integer> l : list) {
            l.add(42);
        }
    }
}