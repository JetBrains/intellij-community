import java.util.stream.Stream;

public class Map {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(1, 2, 3).map(x -> x * x).toArray();
  }
}
