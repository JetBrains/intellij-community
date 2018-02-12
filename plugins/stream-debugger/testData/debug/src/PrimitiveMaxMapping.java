import java.util.OptionalLong;
import java.util.stream.LongStream;

public class PrimitiveMaxMapping {
  public static void main(String[] args) {
    // Breakpoint!
    final OptionalLong res = LongStream.of(1, 2, 3).max();
  }
}