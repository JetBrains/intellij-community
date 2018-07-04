import java.util.Arrays;
import java.util.List;

public class ParallelStream {
  public static void main(String[] args) {
    List<Integer> ints = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 4, 4, 5, 45, 23, 5345);
    // Breakpoint!
    final Integer[] res = ints.stream().parallel().sorted().toArray(Integer[]::new);
  }
}
