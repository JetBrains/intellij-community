import java.util.stream.Stream;

public class AnyMatchMappingTrue {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(1, 2).anyMatch(x -> x % 2 == 0);
  }
}