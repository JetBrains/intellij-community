import java.util.Arrays;
import java.util.stream.Stream;

public class NotImportedLambdaResult {
  public static void main(String[] args) {
    // Breakpoint!
    final int[] values = Stream.of(1).flatMapToInt(x -> Arrays.stream(new int[] {1})).limit(1).toArray();
  }
}
