import java.util.stream.Stream;

public class Skip {
  public static void main(String[] args) {
    // Breakpoint!
    Object[] res = Stream.of(1, 2, 3).skip(2).toArray();
  }
}
