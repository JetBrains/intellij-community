// RUNTIME_WITH_FULL_JDK

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class Collections {
    void test() {
        List<String> x = Arrays.asList("A", "B");
        List<String> xx = Arrays.asList("A", null);
        List<String> y = List.of("C");
        Set<String> z = Set.of("D");
    }
}
