import java.util.*;
import java.util.function.Supplier;

class A {
    void lambda(List<String> items) {
        Supplier<List<String>> supplier = () -> items;
        supplier.get().add("a");
    }

    void blockLambda(List<String> items) {
        Supplier<List<String>> supplier = () -> {
            return items;
        };
        supplier.get().add("a");
    }

    void libraryLambda(List<String> items) {
        ThreadLocal.withInitial(() -> items);
    }

    public List<String> switchResult(int k, List<String> first, List<String> second) {
        return switch (k) {
            case 0 -> first;
            default -> second;
        };
    }
}
