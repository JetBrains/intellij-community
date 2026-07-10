import java.util.Arrays;

public class StopInsideQualifier {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Arrays.stream(makeArray()).map(x -> x + 1).sum();
  }

  private static int[] makeArray() {
    return new int[]{1, 2, 3};
  }
}
