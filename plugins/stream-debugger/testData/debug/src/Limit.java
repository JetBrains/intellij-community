import java.util.stream.Stream;

public class Limit {
  public static void main(String[] args) {
    // Breakpoint!
    Object[] res = Stream.of(1, 2, 3).limit(1).toArray();
  }
}
