import java.util.*;

class Test {
  void method(ThreadLocal<List<? extends String>> l) {
    l.add("");
  }
}