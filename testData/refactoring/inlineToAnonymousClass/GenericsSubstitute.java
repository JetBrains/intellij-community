import java.util.*;

class A {
    private Inner<String> b = new Inner<String>();

    private class <caret>Inner<T> implements Comparator<T> {
        public int compare(T s1, T s2) {
            return 0;
        }
    }
}