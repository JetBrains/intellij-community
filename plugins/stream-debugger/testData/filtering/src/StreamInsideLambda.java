import java.util.stream.Stream;

public class StreamInsideLambda {
  public static void main(String[] args) {
    Stream.of(1, 2).map(x -> {
      // Breakpoint!
      return Stream.of(x).count();
    }).toList();
  }
}
