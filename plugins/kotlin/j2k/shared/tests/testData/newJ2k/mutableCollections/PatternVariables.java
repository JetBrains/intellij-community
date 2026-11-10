import java.util.*;

class A {
    void changedThroughPattern(Collection<String> items) {
        if (items instanceof List<String> list) {
            list.add("a");
        }
    }

    int readThroughPattern(Collection<String> items) {
        if (items instanceof List<String> list) {
            return list.size();
        }
        return 0;
    }
}
