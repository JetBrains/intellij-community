import java.util.stream.Stream;

public class NoneMatchMappingFalse {
  public static void main(String[] args) {
    // Breakpoint!
    final boolean res = Stream.of(1, 2, 3).noneMatch(Integer.class::isInstance);
  }
}
