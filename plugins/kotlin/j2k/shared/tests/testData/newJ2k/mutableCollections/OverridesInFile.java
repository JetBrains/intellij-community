import java.util.*;

interface Handler {
    void handle(List<String> items);

    void log(List<String> items);
}

class Adding implements Handler {
    @Override
    public void handle(List<String> items) {
        items.add("a");
    }

    @Override
    public void log(List<String> items) {
        System.out.println(items.size());
    }
}

class Reading implements Handler {
    @Override
    public void handle(List<String> items) {
        System.out.println(items.size());
    }

    @Override
    public void log(List<String> items) {
        System.out.println(items.size());
    }
}

abstract class Base {
    abstract List<String> names();
}

class Derived extends Base {
    @Override
    List<String> names() {
        return new ArrayList<>();
    }

    void use() {
        names().add("a");
    }
}
