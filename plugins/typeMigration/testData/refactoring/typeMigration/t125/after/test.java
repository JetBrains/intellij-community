import java.util.*;

class Test {
  public <U> List<U> meth(Integer p) {
    return Collections.singletonList(p);
  }
}