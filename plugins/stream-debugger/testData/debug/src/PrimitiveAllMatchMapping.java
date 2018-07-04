import java.util.stream.IntStream;

public class PrimitiveAllMatchMapping {
  public static void main(String[] args) {
    // Breakpoint!
    final boolean res = IntStream.iterate(0, x -> x + 1).limit(5).allMatch(x -> x >= 0);
  }
}