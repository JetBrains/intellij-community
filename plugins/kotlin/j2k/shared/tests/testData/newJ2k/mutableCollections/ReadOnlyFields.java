import java.util.*;

class A {
    private List<String> read = new ArrayList<>();
    private List<String> changed = new ArrayList<>();
    private List<String> changedThroughGetter = new ArrayList<>();
    List<String> packageRead = new ArrayList<>();
    public List<String> publicRead = new ArrayList<>();

    int size() {
        return read.size() + packageRead.size() + publicRead.size();
    }

    void change() {
        changed.add("a");
    }

    List<String> getChangedThroughGetter() {
        return changedThroughGetter;
    }

    void changeThroughGetter() {
        getChangedThroughGetter().add("a");
    }
}
