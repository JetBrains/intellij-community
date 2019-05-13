import java.util.OptionalInt;
import java.util.stream.IntStream;

public class PrimitiveFindFirstMapping {
  public static void main(String[] args) {
    // Breakpoint!
    final OptionalInt res = IntStream.of(1, 2).skip(2).findAny();
  }
}