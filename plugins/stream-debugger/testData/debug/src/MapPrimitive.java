import java.util.stream.LongStream;

public class MapPrimitive {
  public static void main(String[] args) {
    // Breakpoint!
    final long res = LongStream.of(1, 2, 3, 4).map(x -> x - 1).sum();
  }
}
