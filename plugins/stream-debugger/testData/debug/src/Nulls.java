import java.util.stream.Stream;

public class Nulls {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(null, null).count();
  }
}
