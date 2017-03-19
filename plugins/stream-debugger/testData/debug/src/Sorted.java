import java.util.stream.Stream;

public class Sorted {
  public static void main(String[] args) {
    // Breakpoint!
    final Object[] result = Stream.of(3, 1, 2).sorted().toArray();
  }
}
