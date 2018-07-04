import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LinkedChains {
  public static void main(String[] args) {
    // Breakpoint!
    final List<Integer> result = Stream
      .of(1, 2, 3, 4, 5, 6).map(x -> x * x).collect(Collectors.toList())
      .stream().filter(x -> x % 2 == 0).collect(Collectors.toList())
      .stream().sorted().collect(Collectors.toList())
      .stream().distinct().collect(Collectors.toList());
  }
}
