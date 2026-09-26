import java.util.stream.Stream;

public class TwoStatementsOneLine {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Stream.of(1, 2).toList(); Stream.of(3, 4).toList();
  }
}
