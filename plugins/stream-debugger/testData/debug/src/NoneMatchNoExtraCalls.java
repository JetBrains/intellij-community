import java.util.stream.Stream;

public class NoneMatchNoExtraCalls {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Stream.of(3).noneMatch(x -> {
      System.out.println("called");
      return false;
    });
  }
}
