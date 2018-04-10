import java.util.stream.Stream;

public class NoneMatchMappingTrue {
  public static void main(String[] args) {
    // Breakpoint!
    final boolean res = Stream.of(1, 2, 3).noneMatch(x -> x == 5);
  }
}