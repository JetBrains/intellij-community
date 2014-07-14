import java.util.*;
class Test {
    HashMap<String, List<String>> f;

    void foo(String s, String s1) {
      List<String> set = f.get(s);
      if (set == null) {
        set = new HashSet<String>();
        f.put(s, set);
      }
      set.add(s1);
    }
}

class HashMap<K, V> extends java.util.HashMap<K, V>{}