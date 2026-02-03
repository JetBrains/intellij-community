import java.util.stream.Stream;

public class Peek {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(1, 2, 3).peek(x -> {}).toArray();
  }
}
