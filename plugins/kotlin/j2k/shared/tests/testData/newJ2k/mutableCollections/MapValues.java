import java.util.*;

class A {
    void computeIfAbsentChanged(Map<String, List<String>> map) {
        map.computeIfAbsent("k", key -> new ArrayList<>()).add("v");
    }

    void valuesChangedThroughLambda(Map<String, List<String>> map) {
        map.values().forEach(list -> list.add("v"));
    }

    void valuesChangedThroughLoop(Map<String, List<String>> map) {
        for (List<String> list : map.values()) {
            list.clear();
        }
    }

    int valuesRead(Map<String, List<String>> map) {
        int size = 0;
        for (List<String> list : map.values()) {
            size += list.size();
        }
        return size;
    }

    void entryValueChanged(Map<String, List<String>> map) {
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            entry.getValue().add("v");
        }
    }
}
