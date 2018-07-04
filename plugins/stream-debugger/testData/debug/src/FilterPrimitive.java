import java.util.stream.LongStream;

public class FilterPrimitive {
  public static void main(String[] args) {
    // Breakpoint!
    final long res = LongStream.of(1, 2, 3, 4).filter(x -> x % 2 == 0).count();
  }
}
