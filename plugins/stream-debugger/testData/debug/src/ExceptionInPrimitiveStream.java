import java.util.stream.IntStream;

public class ExceptionInPrimitiveStream {
  public static void main(String[] args) {
    // Breakpoint!
    final int res = IntStream.of(1, 2, 3).peek(x -> {
      throw new RuntimeException();
    }).reduce(Integer.MAX_VALUE, Math::min);
  }
}
