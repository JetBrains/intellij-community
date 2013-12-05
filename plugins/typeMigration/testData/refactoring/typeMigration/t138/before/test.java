import java.util.*;
class Test {
  Set<String> mySet = new HashSet();
  void foo(Set<String> set) {
    mySet.retainAll(set);
  }
}