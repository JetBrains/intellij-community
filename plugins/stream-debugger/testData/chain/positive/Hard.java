package example;

  import java.util.stream.IntStream;
  import java.util.stream.Stream;

public class Test {
  public static boolean isPrime(int n) {
    return n > 1 && IntStream.range(2, n).noneMatch(i -> n % i == 0);
  }

  // Find the total of sqrt of first k primes starting with n
  public static double compute(int n, int k) {
<caret>     return Stream.iterate(n, e -> e + 1)
      .filter(Test::isPrime)
      .mapToDouble(Math::sqrt)
      .limit(k)
      .sum();
  }

  public static void main(String[] args) {
    System.out.println(compute(101, 51));
  }
}