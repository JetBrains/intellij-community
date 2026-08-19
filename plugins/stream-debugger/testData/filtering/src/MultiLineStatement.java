import java.util.stream.IntStream;

public class MultiLineStatement {
  public static void main(String[] args) {
    // Breakpoint!
    int total = IntStream.of(1, 2, 3).sum()
                + IntStream.of(4, 5, 6).sum()
                + IntStream.of(7, 8, 9).sum();
    System.out.println(total);
  }
}
