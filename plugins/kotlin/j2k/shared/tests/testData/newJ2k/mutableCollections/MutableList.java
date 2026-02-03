// IGNORE_K2
import java.util.*;

public class J {
    Comparator<Double> natural = Comparator.naturalOrder();

    void foo(
            List<Double> l1, // KTIJ-29149
            List<Double> l2,
            List<Double> l3,
            List<Double> l4,
            List<Double> l5,
            List<Double> l6,
            List<Double> l7,
            List<Double> l8,
            List<Double> l9,
            List<Double> l10,
            List<Double> l11,
            List<Double> l12,
            List<Double> l13,
            List<Double> l14
    ) {
        l1.sort(natural);
        l2.add(1.0);
        l3.addFirst(1.0);
        l4.addLast(1.0);
        l5.addAll(l3);
        l6.clear();
        l7.remove(0);
        l8.remove(1.0);
        l9.removeAll(l1);
        l10.removeFirst();
        l11.removeLast();
        l12.replaceAll(n -> n * n);
        l13.retainAll(l13);
        l14.set(0, 1.0);
    }
}
