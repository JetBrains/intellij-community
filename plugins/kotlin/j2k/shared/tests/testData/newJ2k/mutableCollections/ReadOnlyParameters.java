import java.util.*;

class A {
    public List<String> field = new ArrayList<>();

    void read(List<String> items) {
        System.out.println(items);
        String joined = String.join(",", items);
        List<String> copy = new ArrayList<>(items);
    }

    void shuffledByLibrary(List<String> items) {
        Collections.shuffle(items);
    }

    void storedInField(List<String> items) {
        field = items;
    }

    void passedToMutatingParameter(List<String> items) {
        add(items);
    }

    void add(List<String> items) {
        items.add("a");
    }

    void entryChanged(Map<String, Integer> map) {
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            entry.setValue(1);
        }
    }

    void keySetChanged(Map<String, Integer> map) {
        map.keySet().remove("a");
    }

    void addedByMethodReference(List<String> items, List<String> source) {
        source.forEach(items::add);
    }

    void callback() {
        Callback callback = items -> items.add("a");
    }
}

@FunctionalInterface
interface Callback {
    void call(List<String> items);
}
