import java.util.stream.LongStream;

public class DistinctPrimitive {
  public static void main(String[] args) {
    // Breakpoint!
    final long res = LongStream.of(1, 1, 1).distinct().count();
  }
}
