import java.util.stream.Stream;

public class AllMatchNoExtraCalls {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Stream.of(2).allMatch(x -> {
      System.out.println("called");
      return false;
    });
  }
}
