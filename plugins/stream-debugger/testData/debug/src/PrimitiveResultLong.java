import java.util.stream.IntStream;

public class PrimitiveResultLong {
  public static void main(String[] args) {
    // Breakpoint!
    long res = IntStream.of(1, 2).count();
  }
}
