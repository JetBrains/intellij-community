import java.util.stream.Stream;

public class PrimitiveResultBoolean {
  public static void main(String[] args) {
    // Breakpoint!
    boolean res = Stream.of(1).anyMatch(x -> x == 2);
  }
}
