import java.util.stream.Stream;

public class ShortCircuitingAfterSorted {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(3, 2, 1).sorted().findFirst();
  }
}
