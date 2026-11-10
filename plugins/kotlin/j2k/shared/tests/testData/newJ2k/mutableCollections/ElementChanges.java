import java.util.*;

class A {
    void entryThroughIterator(Map<String, Integer> map) {
        map.entrySet().iterator().next().setValue(1);
    }

    void entryThroughLambda(Map<String, Integer> map) {
        map.entrySet().forEach(e -> e.setValue(1));
    }

    void entryReadThroughLambda(Map<String, Integer> map) {
        map.entrySet().forEach(e -> System.out.println(e.getValue()));
    }

    void entryThroughLoop(Map<String, Integer> map) {
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            entry.setValue(1);
        }
    }

    void innerListChanged(List<List<String>> lists) {
        for (List<String> list : lists) {
            list.add("a");
        }
    }

    void innerListRead(List<List<String>> lists) {
        for (List<String> list : lists) {
            System.out.println(list.size());
        }
    }

    void innerListChangedThroughGet(Map<String, List<String>> map) {
        map.get("a").add("b");
    }
}
