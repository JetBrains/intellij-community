import java.util.stream.Stream;

public class NoIntermediate {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of("a", "b", "c").count();
  }
}
