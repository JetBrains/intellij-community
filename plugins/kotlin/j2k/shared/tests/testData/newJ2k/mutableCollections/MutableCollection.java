import java.util.*;

public class J {
    void foo(
            Collection<Double> l1,
            Collection<Double> l2,
            Collection<Double> l3,
            Collection<Double> l4,
            Collection<Double> l5,
            Collection<Double> l6,
            Collection<Double> l7
    ) {
        l1.add(1.0);
        l2.addAll(l3);
        l3.clear();
        l4.remove(1.0);
        l5.removeAll(l1);
        l6.removeIf(l2::contains);
        l7.retainAll(l2);
    }
}
