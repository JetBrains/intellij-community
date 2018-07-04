import java.util.stream.Stream;

public class NestedStreamIntermediateCallParameter {
  public static void main(String[] args) {
<caret>    Stream.iterate(0, i -> i + 1).skip(Stream.of(1).skip(Stream.empty().count()).count()).limit(1).forEach(x -> {
    });
  }
}
