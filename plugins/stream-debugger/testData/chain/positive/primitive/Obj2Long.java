import java.util.stream.Stream;

public class Baz {
  public static void main(String[] args) {
<caret>    final long res = Stream.of(1,2,3).mapToLong(Integer::longValue).sum();
  }
}
