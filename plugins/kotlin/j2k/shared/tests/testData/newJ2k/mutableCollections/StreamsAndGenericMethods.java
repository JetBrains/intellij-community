import java.util.*;
import java.util.stream.Collectors;

class A {
    void collectedAndChanged(List<String> items) {
        List<String> copy = items.stream().collect(Collectors.toList());
        copy.add("a");
    }

    void changedThroughGenericMethod(List<String> items) {
        List<String> same = identity(items);
        same.add("a");
    }

    <T> T identity(T value) {
        return value;
    }

    void changedThroughGenericParameter(List<String> items) {
        addTo(items, "a");
    }

    <T> void addTo(List<T> list, T value) {
        list.add(value);
    }
}
