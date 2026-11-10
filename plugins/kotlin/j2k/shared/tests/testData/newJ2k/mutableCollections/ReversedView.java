import java.util.*;

class A {
    void changed(List<String> list) {
        list.reversed().add("a");
    }

    String read(List<String> list) {
        return list.reversed().get(0);
    }
}
