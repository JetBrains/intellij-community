import java.util.*;
public class Test {
  void method(List<? super Number> l) {
    for (Object integer : l) {
      System.out.println(integer.hashCode());
    }
  }
}
