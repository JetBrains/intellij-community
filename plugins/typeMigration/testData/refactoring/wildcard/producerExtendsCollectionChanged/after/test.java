import java.util.*;

class Test {
  void method(Set<? extends Object> p) {
    p.add(new Integer(8));
  }
}