import java.util.stream.IntStream;

public class DistinctHardPrimitive {
  public static void main(String[] args) {
    // Breakpoint!
    final long res = IntStream.of(1, 2, 1).distinct().count();
  }
}
