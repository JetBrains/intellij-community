import java.util.stream.IntStream;

public class ManyIntermediates {
  public static void main(String[] args) {
    // Breakpoint!
    IntStream.of(1, 2, 3, 4, 5).map(x -> x + 1).filter(x -> x > 0).map(x -> x * 2).sum();
  }
}
