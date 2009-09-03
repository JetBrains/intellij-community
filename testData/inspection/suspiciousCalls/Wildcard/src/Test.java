import java.util.List;
import java.util.ArrayList;

class Clazz {
    void foo(List<? extends Number> l) {
      boolean b = l.contains("");
    }
}