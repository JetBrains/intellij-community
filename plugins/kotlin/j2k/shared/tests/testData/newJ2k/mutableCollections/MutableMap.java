import java.util.*;

public class J {
    void foo(
            Map<String, String> m1,
            Map<String, String> m2,
            Map<String, String> m3,
            Map<String, String> m4,
            Map<String, String> m5,
            Map<String, String> m6,
            Map<String, String> m7,
            Map<String, String> m8,
            Map<String, String> m9,
            Map<String, String> m10,
            Map<String, String> m11
    ) {
        m1.clear();
        m2.compute("m1", (k, v) -> v);
        m3.computeIfAbsent("m2", (k) -> "value");
        m4.computeIfPresent("m1", (k, v) -> v);
        m5.merge("", "", (k, v) -> v);
        m6.put("m1", "value");
        m7.putAll(m8);
        m8.putIfAbsent("m2", "value");
        m9.remove("");
        m10.replace("m2", "value");
        m11.replaceAll((k, v) -> v + "2");
    }
}
