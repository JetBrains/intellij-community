import java.util.Map;
public class Test {
    Map<String, Integer> f;
    void foo() {
      for (String i  : f.keySet()) {}
    }
}
