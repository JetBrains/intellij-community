import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

public class Baz {
  public static void bar() {
<caret>    final IntSummaryStatistics stat = IntStream.of(1, 12, 2).summaryStatistics();
  }
}
