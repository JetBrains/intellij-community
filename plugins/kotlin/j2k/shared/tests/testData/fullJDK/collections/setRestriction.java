// IGNORE_K2

import java.util.Set;

public class Collections {
    void test() {
        Set<String> x = Set.of("A", "A");
        Set<String> y = Set.of("A", null);
        Set<String> z = Set.of(null);
    }
}
