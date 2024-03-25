// IGNORE_K2

import java.util.Map;

class C {
    private String foo(Map<Integer, String> map) {
        return map.getOrDefault(1, "bar");
    }
}