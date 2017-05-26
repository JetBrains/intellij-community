import java.util.stream.Stream;

public class AnyMatchExtraCalls {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(1).anyMatch(x -> {
      System.out.println("called");
      return false;
    });
  }
}
