import java.util.Arrays;

public class J {
    void foo(Object[] a) {
        Arrays.copyOf(a, 5);
        Arrays.copyOf(a, 5, Object[].class); // not applicable
        //
        Arrays.copyOfRange(a, 5, 6);
        Arrays.copyOfRange(a, 5, 6, Object[].class); // not applicable
        //
        Arrays.equals(a, a);
        Arrays.deepEquals(a, a);
        //
        Arrays.hashCode(a);
        Arrays.deepHashCode(a);
        //
        Arrays.toString(a);
        Arrays.deepToString(a);
    }
}