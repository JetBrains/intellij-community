import java.util.stream.LongStream;

public class SortedPrimitive {
  public static void main(String[] args) {
    // Breakpoint!
    final long[] result = LongStream.of(3, 1, 2, 3, 3, 3, 3, 1, 2, 2, 3, 4, 5, 2).sorted().toArray();
  }
}
