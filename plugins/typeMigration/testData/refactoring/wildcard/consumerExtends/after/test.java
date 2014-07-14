import java.util.*;

class Test {
  void method(ArrayList<? extends Number> p, Number p2) {
    p2 = p.get(0);
  }
}