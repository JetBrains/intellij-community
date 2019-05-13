import java.util.stream.Stream;

public class ToArrayMapping {
  public static void main(String[] args) {
    // Breakpoint!
    final Integer[] integers = Stream.of(10, 11, 12).toArray(Integer[]::new);
  }
}