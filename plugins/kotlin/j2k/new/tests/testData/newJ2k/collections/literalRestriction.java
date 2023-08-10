// RUNTIME_WITH_FULL_JDK

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class Collections {
    void test() {
        String field = "A";
        String[] arr = {"A"};
        List<String> x1 = Arrays.asList(field);
        List<String> x2 = Arrays.asList(arr);
        List<String> y1 = List.of(field);
        List<String> y2 = List.of(arr);
        Set<String> z1 = Set.of(field);
        Set<String> z2 = Set.of(arr);
    }
}
