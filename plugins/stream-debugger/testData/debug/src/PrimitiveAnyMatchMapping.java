import java.util.stream.IntStream;

public class PrimitiveAnyMatchMapping {
  public static void main(String[] args) {
    // Breakpoint!
    final boolean res = IntStream.iterate(0, x -> x + 1).limit(5).anyMatch(x -> x < 0);
  }
}