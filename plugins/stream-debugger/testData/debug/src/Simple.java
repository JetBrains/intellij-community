import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Simple {
  public static void main(String[] args) {
    // Breakpoint!
    final List<Integer> res = Stream.of(1).collect(Collectors.toList());
  }
}