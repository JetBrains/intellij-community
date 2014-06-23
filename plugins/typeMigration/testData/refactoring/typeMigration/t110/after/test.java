import java.util.*;
public class Test {
  void method(List<? extends String> l) {
    for (String integer : l) {
      System.out.println(integer.intValue());
    }
  }
}
