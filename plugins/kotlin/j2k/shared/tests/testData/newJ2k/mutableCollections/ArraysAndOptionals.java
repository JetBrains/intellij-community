import java.util.*;

class A {
    void changedThroughArray(List<String>[] lists) {
        lists[0].add("a");
    }

    int readThroughArray(List<String>[] lists) {
        return lists[0].size();
    }

    void changedThroughOptional(Optional<List<String>> optional) {
        optional.get().add("a");
    }

    void changedThroughOptionalLambda(Optional<List<String>> optional) {
        optional.ifPresent(list -> list.add("a"));
    }

    int readThroughOptional(Optional<List<String>> optional) {
        return optional.get().size();
    }
}
