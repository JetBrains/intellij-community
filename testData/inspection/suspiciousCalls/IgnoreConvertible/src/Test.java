import java.util.List;
import java.util.ArrayList;

class Clazz {
  void f(Map<Integer,String> map) {
    Number n = new Integer(3);
    if (map.containsKey(n)) {
      return;
    }
  }

  void foo(List<? extends Number> c) {
    c.contains("");
  }
}