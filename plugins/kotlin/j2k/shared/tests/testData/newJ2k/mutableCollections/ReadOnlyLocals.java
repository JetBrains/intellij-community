import java.util.*;

class A {
    int readOnly(Collection<String> items) {
        List<String> copy = new ArrayList<>(items);
        return copy.size();
    }

    void mutated() {
        List<String> list = new ArrayList<>();
        list.add("a");
    }

    public List<String> returned() {
        List<String> list = new ArrayList<>();
        return list;
    }

    void passedToMutatingParameter() {
        List<String> list = new ArrayList<>();
        add(list);
    }

    void add(List<String> list) {
        list.add("a");
    }

    void iterated(Collection<String> items) {
        Iterator<String> iterator = items.iterator();
        while (iterator.hasNext()) {
            iterator.next();
        }
    }

    void removedByIterator(Collection<String> items) {
        Iterator<String> iterator = items.iterator();
        iterator.next();
        iterator.remove();
    }
}
