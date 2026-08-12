import java.util.stream.IntStream;

public class TwoIndependentChains {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    int sum = IntStream.of(1, 2, 3).sum() + IntStream.of(4, 5, 6).filter(x -> x > 0).sum();
    System.out.println(sum);
  }
}
