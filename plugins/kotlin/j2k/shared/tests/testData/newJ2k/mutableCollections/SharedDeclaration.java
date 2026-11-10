import java.util.*;

class A {
    private List<String> readField = new ArrayList<>(), changedField = new ArrayList<>();
    private List<String> readFirst = new ArrayList<>(), readSecond = new ArrayList<>();

    void locals() {
        List<String> read = new ArrayList<>(), changed = new ArrayList<>();
        changed.add("a");
        System.out.println(read);
    }

    void fields() {
        changedField.add("a");
        System.out.println(readField);
        System.out.println(readFirst.size() + readSecond.size());
    }
}
