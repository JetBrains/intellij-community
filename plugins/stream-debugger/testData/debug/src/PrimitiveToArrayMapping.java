import java.util.stream.IntStream;

public class PrimitiveToArrayMapping {
  public static void main(String[] args) {
    // Breakpoint!
    final int[] ints = IntStream.of(1, 2, 3, 4).toArray();
  }
}