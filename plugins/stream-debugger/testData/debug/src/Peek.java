import java.util.stream.Stream;

public class Peek {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Stream.of(1, 2, 3).peek(x -> {}).toArray();
  }
}
