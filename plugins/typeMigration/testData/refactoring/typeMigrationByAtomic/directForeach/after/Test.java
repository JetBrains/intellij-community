import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class Test {
    AtomicReference<List<String>> lst;

    void foo() {
      for (String s : lst.get()) {
        System.out.println(s);
      }
    }
}