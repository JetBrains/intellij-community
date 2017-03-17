import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Filter {
  public static void main(String[] args) {
    // Breakpoint!
    final List<Integer> res = Stream.of(1, 2, 3, 4).filter(x -> x % 2 == 1).collect(Collectors.toList());
  }
}