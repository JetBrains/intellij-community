import java.util.stream.Stream;

public class NestedStreamProducerParameter {
  public static void main(String[] args) {
<caret>    Stream.of(Stream.of(Stream.of(1).count()).count()).count();
  }
}
