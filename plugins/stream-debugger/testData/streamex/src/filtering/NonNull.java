package filtering;

import one.util.streamex.StreamEx;

public class NonNull {
  public static void main(String[] args) {
    final Integer[] array = new Integer[]{1, 2, 3, 4, null, null};
    // Breakpoint!
    final long res = StreamEx.of(array).nonNull().count();
    System.out.println(res);
  }
}
