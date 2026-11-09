import java.util.*;

public class J {
    void foo(Set<Integer> l0, Set<Integer> l1, Set<Integer> l2, Set<Integer> l3, Set<Integer> l4, Set<Integer> l5) {
        l0.add(1);
        l1.addAll(l5);
        l2.clear();
        l3.removeAll(l5);
        l4.retainAll(l5);
        l5.contains(1);
    }
}