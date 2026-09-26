import java.util.List;

public class TwoIdenticalChainsSameLine {
  public static void main(String[] args) {
    List<Integer> arr = List.of(1, 2, 3, 4, 5);
    // Breakpoint! lambdaOrdinal(-1)
    int total = arr.stream().map(x -> x * x).toList().size() + arr.stream().map(x -> x + 1).toList().size();
    System.out.println(total);
  }
}
