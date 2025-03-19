import java.util.List;
import java.util.Map;

public class J {
    void foo(List<String> list, Map<String, String> map) {
        String s1 = list.get(0);
        String s2 = map.get("");

        s1.length(); // not-null assertion
        s2.length(); // not-null assertion
    }
}