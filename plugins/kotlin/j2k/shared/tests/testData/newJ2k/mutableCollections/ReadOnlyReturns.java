import java.util.*;

class A {
    private List<String> names = new ArrayList<>();

    List<String> getNames() {
        return names;
    }

    void add(String name) {
        names.add(name);
    }

    List<String> created() {
        return new ArrayList<>();
    }

    List<String> changedByCaller() {
        return new ArrayList<>();
    }

    List<String> storedByCaller() {
        return new ArrayList<>();
    }

    void callers() {
        changedByCaller().add("a");
        List<String> list = storedByCaller();
        list.add("a");
    }

    public List<String> publicMethod() {
        return new ArrayList<>();
    }
}

class B extends A {
    @Override
    List<String> changedByCaller() {
        return new LinkedList<>();
    }

    @Override
    List<String> created() {
        return new LinkedList<>();
    }
}
