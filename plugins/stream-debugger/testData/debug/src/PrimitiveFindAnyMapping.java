import java.util.OptionalInt;
import java.util.stream.IntStream;

public class PrimitiveFindAnyMapping {
  public static void main(String[] args) {
    // Breakpoint!
    final OptionalInt res = IntStream.of(1, 2).findAny();
  }
}