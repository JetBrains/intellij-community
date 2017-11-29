import java.util.stream.Stream;

public class NoneMatchExtraCalls {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(3).noneMatch(x -> {
      System.out.println("called");
      return false;
    });
  }
}
