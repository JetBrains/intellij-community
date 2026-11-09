import java.util.*;

public class J {
    void foo(ListIterator<Integer> l0, ListIterator<Integer> l1, ListIterator<Integer> l2, ListIterator<Integer> l3) {
        l0.add(1);
        l1.remove();
        l2.set(1);
        l3.next();
    }
}