import java.util.*;

class A {
    void readOnly() {
        List<String> list = List.of("a");
        Set<String> set = Collections.singleton("a");
        List<String> empty = Collections.emptyList();
        List<String> asList = Arrays.asList("a", "b");
    }

    void changed() {
        List<String> list = Arrays.asList("a", "b");
        list.set(0, "c");
    }

    int notStored() {
        return List.of("a").size();
    }
}
