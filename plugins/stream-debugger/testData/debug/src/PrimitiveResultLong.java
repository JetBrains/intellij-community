import java.util.stream.LongStream;

public class PrimitiveResultLong {
  public static void main(String[] args) {
    // Breakpoint!
    long res = LongStream.of(1, 2).sum();
  }
}
